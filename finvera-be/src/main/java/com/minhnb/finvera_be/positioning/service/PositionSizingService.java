package com.minhnb.finvera_be.positioning.service;

import com.minhnb.finvera_be.market.service.MarketReferenceDataService;
import com.minhnb.finvera_be.portfolio.service.PortfolioSizingDataService;
import com.minhnb.finvera_be.positioning.domain.PositionSizingV1;
import com.minhnb.finvera_be.positioning.dto.PositionSizingDtos.*;
import com.minhnb.finvera_be.positioning.service.PositionSizingExceptions.InvalidSizingRequestException;
import com.minhnb.finvera_be.stock.service.StockSizingDataService;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class PositionSizingService {
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final long STANDARD_LOT = 100L;
    private static final String LISTED_LOT_RULE_SOURCE = "VNX_DECISION_22_QD_HDTV_2026";
    private static final String LISTED_LOT_RULE_EFFECTIVE_DATE = "2026-03-16";
    private static final String UPCOM_LOT_RULE_SOURCE = "VNX_DECISION_23_QD_HDTV_2026";
    private static final String UPCOM_LOT_RULE_EFFECTIVE_DATE = "2026-03-18";
    private static final String LOT_RULE_REVIEWED_DATE = "2026-09-13";
    private static final Instant LOT_RULE_ACCEPTED_AT = Instant.parse("2026-09-13T00:00:00Z");
    private final MarketReferenceDataService market;
    private final PortfolioSizingDataService portfolios;
    private final StockSizingDataService stocks;
    private final PositionSizingMetrics metrics;
    private final Clock clock;

    public PositionSizingService(MarketReferenceDataService market, PortfolioSizingDataService portfolios,
            StockSizingDataService stocks, PositionSizingMetrics metrics, Clock clock) {
        this.market = market; this.portfolios = portfolios; this.stocks = stocks; this.metrics = metrics; this.clock = clock;
    }

    public SizingResult calculate(SizingRequest request) {
        Timer.Sample timer = metrics.start();
        String mode = request == null || request.mode() == null ? "UNKNOWN" : request.mode().name();
        try {
            SizingResult result = calculateInternal(request);
            metrics.finish(timer, mode, result.status(), result.reasonCodes().isEmpty() ? "NONE" : result.reasonCodes().get(0));
            return result;
        } catch (InvalidSizingRequestException | PositionSizingV1.ValidationException ex) {
            String reason = ex instanceof PositionSizingV1.ValidationException validation
                    ? publicValidationReason(validation.reasonCode())
                    : ((InvalidSizingRequestException) ex).reasonCode();
            metrics.finish(timer, mode, "INVALID", reason);
            if (ex instanceof PositionSizingV1.ValidationException validation) {
                throw new InvalidSizingRequestException(publicValidationReason(validation.reasonCode()));
            }
            throw ex;
        } catch (RuntimeException ex) {
            metrics.finish(timer, mode, "FAILED", "SERVER_ERROR");
            throw ex;
        }
    }

    private SizingResult calculateInternal(SizingRequest request) {
        if (request == null || request.symbol() == null || request.mode() == null || request.riskBudget() == null
                || request.riskBudget().kind() == null || request.priceInput() == null
                || request.priceInput().source() == null || request.costPolicy() == null
                || request.costPolicy().excludeCosts() == null) throw invalid("INVALID_REQUEST");
        validateShape(request);
        String symbol = request.symbol().toUpperCase(Locale.ROOT);
        var instrument = market.findActiveInstrumentBySymbol(symbol).orElse(null);
        if (instrument == null || !isSupportedVenue(instrument.venue()) || !"EQUITY".equals(instrument.instrumentType())) {
            return withheld(request, symbol, "MARKET_LOT_RULE_UNAVAILABLE", List.of());
        }

        List<InputEvidence> evidence = new ArrayList<>();
        BigDecimal capitalBase;
        BigDecimal availableCash;
        BigDecimal portfolioValue = null;
        BigDecimal existingSymbolValue = null;
        BigDecimal deployedValue = null;
        Instant portfolioAsOf = null;
        String portfolioCoherence = null;
        if (request.mode() == Mode.MANUAL) {
            if (request.portfolioId() != null || request.manualCapital() == null) throw invalid("INVALID_INPUT");
            var m = request.manualCapital();
            capitalBase = decimal(m.capitalBaseVnd(), "capitalBaseVnd");
            availableCash = decimal(m.availableCashVnd(), "availableCashVnd");
            portfolioValue = optionalDecimal(m.portfolioValueVnd(), "portfolioValueVnd");
            existingSymbolValue = optionalDecimal(m.existingSymbolMarketValueVnd(), "existingSymbolMarketValueVnd");
            deployedValue = optionalDecimal(m.currentDeployedMarketValueVnd(), "currentDeployedMarketValueVnd");
            addEvidence(evidence, "capitalBaseVnd", capitalBase, "OWNER_ENTERED", "VND", null, null);
            addEvidence(evidence, "availableCashVnd", availableCash, "OWNER_ENTERED", "VND", null, null);
            addEvidence(evidence, "portfolioValueVnd", portfolioValue, "OWNER_ENTERED", "VND", null, null);
            addEvidence(evidence, "existingSymbolMarketValueVnd", existingSymbolValue, "OWNER_ENTERED", "VND", null, null);
            addEvidence(evidence, "currentDeployedMarketValueVnd", deployedValue, "OWNER_ENTERED", "VND", null, null);
        } else if (request.mode() == Mode.PORTFOLIO) {
            if (request.portfolioId() == null || request.manualCapital() != null) throw invalid("INVALID_INPUT");
            var p = portfolios.resolve(request.portfolioId(), symbol);
            if (!request.portfolioId().equals(p.portfolioId()) || !"CURRENT".equals(p.dataStatus())
                    || p.coherenceKey() == null || p.coherenceKey().isBlank() || p.asOf() == null
                    || p.symbolMarketValueVnd() == null || p.symbolMarketValueVnd().signum() < 0
                    || p.totalValueVnd() == null || p.totalValueVnd().signum() <= 0
                    || p.availableCashVnd() == null || p.availableCashVnd().signum() < 0
                    || p.deployedMarketValueVnd() == null || p.deployedMarketValueVnd().signum() < 0) {
                return withheld(request, symbol, "PORTFOLIO_DATA_UNAVAILABLE", List.of());
            }
            capitalBase = p.totalValueVnd(); availableCash = p.availableCashVnd(); portfolioValue = p.totalValueVnd();
            existingSymbolValue = p.symbolMarketValueVnd(); deployedValue = p.deployedMarketValueVnd();
            portfolioAsOf = p.asOf(); portfolioCoherence = p.coherenceKey();
            addEvidence(evidence, "capitalBaseVnd", capitalBase, "PORTFOLIO", "VND", portfolioAsOf, portfolioCoherence);
            addEvidence(evidence, "availableCashVnd", availableCash, "PORTFOLIO", "VND", portfolioAsOf, portfolioCoherence);
            addEvidence(evidence, "existingSymbolMarketValueVnd", existingSymbolValue, "PORTFOLIO", "VND", portfolioAsOf, portfolioCoherence);
            addEvidence(evidence, "currentDeployedMarketValueVnd", deployedValue, "PORTFOLIO", "VND", portfolioAsOf, portfolioCoherence);
        } else throw invalid("INVALID_MODE");

        BigDecimal entry;
        BigDecimal stop;
        if (request.priceInput().source() == PriceSource.MANUAL) {
            if (request.priceInput().entryPriceVnd() == null || request.priceInput().stopPriceVnd() == null
                    || request.priceInput().strategyCode() != null || request.priceInput().ruleVersion() != null
                    || request.priceInput().calculatedAt() != null || request.priceInput().entryBasis() != null) {
                throw invalid("INVALID_INPUT");
            }
            entry = decimal(request.priceInput().entryPriceVnd(), "entryPriceVnd");
            stop = decimal(request.priceInput().stopPriceVnd(), "stopPriceVnd");
            addEvidence(evidence, "entryPriceVnd", entry, "OWNER_ENTERED", "VND_PER_SHARE", null, null);
            addEvidence(evidence, "stopPriceVnd", stop, "OWNER_ENTERED", "VND_PER_SHARE", null, null);
            var context = request.priceInput().originatingSignalContext();
            if (context != null) {
                String contextKey = "signal-context:" + context.strategyCode() + ":" + context.calculatedAt();
                evidence.add(new InputEvidence("originatingSignalStrategy", context.strategyCode(),
                        "SIGNAL_CONTEXT", "TEXT", context.calculatedAt(), contextKey));
                evidence.add(new InputEvidence("originatingSignalRuleVersion", context.ruleVersion(),
                        "SIGNAL_CONTEXT", "TEXT", context.calculatedAt(), contextKey));
                evidence.add(new InputEvidence("originatingSignalTradingDate", context.asOfTradingDate().toString(),
                        "SIGNAL_CONTEXT", "TEXT", context.calculatedAt(), contextKey));
            }
        } else if (request.priceInput().source() == PriceSource.SIGNAL) {
            var pi = request.priceInput();
            if (!Boolean.TRUE.equals(pi.confirmed())) throw invalid("SIGNAL_NOT_CONFIRMED");
            if (pi.strategyCode() == null || pi.ruleVersion() == null
                    || pi.calculatedAt() == null || pi.entryBasis() == null
                    || pi.entryPriceVnd() != null || pi.stopPriceVnd() != null
                    || pi.originatingSignalContext() != null) throw invalid("INVALID_INPUT");
            var signal = stocks.resolveCurrentLongSignal(symbol,
                    new StockSizingDataService.Selector(pi.strategyCode(), pi.ruleVersion(), pi.calculatedAt()))
                    .orElse(null);
            if (signal == null) return withheld(request, symbol, "SIGNAL_NOT_CURRENT", evidence);
            entry = switch (pi.entryBasis()) {
                case ENTRY_LOW -> signal.entryLow();
                case ENTRY_HIGH -> signal.entryHigh();
                case MIDPOINT -> signal.entryLow().add(signal.entryHigh(), MC).divide(new BigDecimal("2"), MC);
            };
            stop = signal.stopLoss();
            addEvidence(evidence, "entryPriceVnd", entry, "SIGNAL", "VND_PER_SHARE", signal.calculatedAt(), signal.coherenceKey());
            addEvidence(evidence, "stopPriceVnd", stop, "SIGNAL", "VND_PER_SHARE", signal.calculatedAt(), signal.coherenceKey());
            evidence.add(new InputEvidence("signalId", signal.signalId().toString(), "SIGNAL", "TEXT",
                    signal.calculatedAt(), signal.coherenceKey()));
            evidence.add(new InputEvidence("signalTradingDate", signal.tradingDate().toString(), "SIGNAL", "TEXT",
                    signal.calculatedAt(), signal.coherenceKey()));
        } else throw invalid("INVALID_PRICE_SOURCE");

        boolean excluded = request.costPolicy().excludeCosts();
        PositionSizingV1.Costs costs;
        List<String> warnings = new ArrayList<>();
        if (excluded) {
            if (hasAnyCost(request.costPolicy())) throw invalid("INCOMPLETE_COST_POLICY");
            costs = PositionSizingV1.Costs.excluded();
            warnings.addAll(List.of("COSTS_EXCLUDED", "ENTRY_FEE_EXCLUDED", "EXIT_FEE_EXCLUDED",
                    "SELL_TAX_EXCLUDED", "ENTRY_SLIPPAGE_EXCLUDED", "EXIT_SLIPPAGE_EXCLUDED"));
        } else {
            if (allCostsZero(request.costPolicy())) throw invalid("INCOMPLETE_COST_POLICY");
            costs = new PositionSizingV1.Costs(
                    decimal(request.costPolicy().entryFeeRate(), "entryFeeRate"),
                    decimal(request.costPolicy().exitFeeRate(), "exitFeeRate"),
                    decimal(request.costPolicy().sellTaxRate(), "sellTaxRate"),
                    decimal(request.costPolicy().entrySlippageRate(), "entrySlippageRate"),
                    decimal(request.costPolicy().exitSlippageRate(), "exitSlippageRate"));
            addEvidence(evidence, "entryFeeRate", costs.entryFeeRate(), "OWNER_ENTERED", "DECIMAL_RATE", null, null);
            addEvidence(evidence, "exitFeeRate", costs.exitFeeRate(), "OWNER_ENTERED", "DECIMAL_RATE", null, null);
            addEvidence(evidence, "sellTaxRate", costs.sellTaxRate(), "OWNER_ENTERED", "DECIMAL_RATE", null, null);
            addEvidence(evidence, "entrySlippageRate", costs.entrySlippageRate(), "OWNER_ENTERED", "DECIMAL_RATE", null, null);
            addEvidence(evidence, "exitSlippageRate", costs.exitSlippageRate(), "OWNER_ENTERED", "DECIMAL_RATE", null, null);
        }

        BigDecimal riskValue = decimal(request.riskBudget().value(), "riskBudget.value");
        BigDecimal riskBudget = switch (request.riskBudget().kind()) {
            case FIXED_VND -> riskValue;
            case PERCENT -> {
                if (riskValue.signum() <= 0 || riskValue.compareTo(BigDecimal.ONE) > 0) throw invalid("INVALID_RISK_BUDGET");
                yield capitalBase.multiply(riskValue, MC);
            }
        };
        addEvidence(evidence, "riskBudgetInput", riskValue, "OWNER_ENTERED",
                request.riskBudget().kind() == RiskKind.PERCENT ? "DECIMAL_RATE" : "VND", null, portfolioCoherence);
        addEvidence(evidence, "riskBudgetVnd", riskBudget, "OWNER_ENTERED", "VND", null, portfolioCoherence);

        BigDecimal symbolCap = request.exposureLimits() == null ? null
                : optionalDecimal(request.exposureLimits().maxSymbolConcentrationRate(), "maxSymbolConcentrationRate");
        BigDecimal deploymentCap = request.exposureLimits() == null ? null
                : optionalDecimal(request.exposureLimits().maxDeploymentRate(), "maxDeploymentRate");
        if (request.exposureLimits() != null && symbolCap == null && deploymentCap == null) {
            throw invalid("INVALID_INPUT");
        }
        addEvidence(evidence, "maxSymbolConcentrationRate", symbolCap, "OWNER_ENTERED", "DECIMAL_RATE", null, null);
        addEvidence(evidence, "maxDeploymentRate", deploymentCap, "OWNER_ENTERED", "DECIMAL_RATE", null, null);
        var engine = PositionSizingV1.calculate(new PositionSizingV1.Input(capitalBase, availableCash, entry, stop,
                riskBudget, costs, portfolioValue, existingSymbolValue, deployedValue, symbolCap, deploymentCap, STANDARD_LOT));
        evidence.add(new InputEvidence("resolvedSymbol", symbol, "MARKET_RULE", "TEXT",
                LOT_RULE_ACCEPTED_AT, "market-lot-v1"));
        evidence.add(new InputEvidence("venue", instrument.venue(), "MARKET_RULE", "TEXT",
                LOT_RULE_ACCEPTED_AT, "market-lot-v1"));
        evidence.add(new InputEvidence("instrumentStatus", instrument.status(), "MARKET_RULE", "TEXT",
                LOT_RULE_ACCEPTED_AT, "market-lot-v1"));
        addEvidence(evidence, "marketLotSize", BigDecimal.valueOf(STANDARD_LOT), "MARKET_RULE", "SHARES",
                LOT_RULE_ACCEPTED_AT, "market-lot-v1");
        boolean upcom = "UPCOM".equals(instrument.venue());
        evidence.add(new InputEvidence("marketRuleSource",
                upcom ? UPCOM_LOT_RULE_SOURCE : LISTED_LOT_RULE_SOURCE,
                "MARKET_RULE", "TEXT", LOT_RULE_ACCEPTED_AT, "market-lot-v1"));
        evidence.add(new InputEvidence("marketRuleEffectiveDate",
                upcom ? UPCOM_LOT_RULE_EFFECTIVE_DATE : LISTED_LOT_RULE_EFFECTIVE_DATE,
                "MARKET_RULE", "TEXT", LOT_RULE_ACCEPTED_AT, "market-lot-v1"));
        evidence.add(new InputEvidence("marketRuleReviewedDate", LOT_RULE_REVIEWED_DATE,
                "MARKET_RULE", "TEXT", LOT_RULE_ACCEPTED_AT, "market-lot-v1"));
        return map(request, symbol, engine, capitalBase, availableCash, entry, stop, excluded, evidence, warnings);
    }

    private SizingResult map(SizingRequest req, String symbol, PositionSizingV1.Result r, BigDecimal capital,
            BigDecimal cash, BigDecimal entry, BigDecimal stop, boolean excluded, List<InputEvidence> evidence,
            List<String> warnings) {
        return new SizingResult(r.calculated() ? "CALCULATED" : "WITHHELD", symbol, req.mode().name(), r.quantity(),
                r.rawPermittedQuantity(), r.lotSize(), r.roundingRemainder(), s(capital), s(cash), s(entry), s(stop),
                s(r.effectiveEntryPriceVnd()), s(r.effectiveStopPriceVnd()), excluded, s(r.riskBudgetVnd()),
                s(r.acquisitionUnitCostVnd()), s(r.stopNetProceedsPerShareVnd()), s(r.lossPerShareVnd()),
                s(r.requiredCapitalVnd()), s(r.estimatedLossAtStopVnd()), s(r.remainingCashVnd()),
                s(r.projectedSymbolMarketValueVnd()), s(r.projectedSymbolExposureRate()), s(r.projectedDeploymentRate()),
                r.constraints().stream().map(c -> new ConstraintResult(c.code().name(), c.applicability().name(),
                        c.rawQuantity(), c.binding())).toList(), List.copyOf(evidence), r.reasonCodes(), List.copyOf(warnings),
                PositionSizingV1.RULE_VERSION, PositionSizingV1.LOT_RULE_VERSION, Instant.now(clock));
    }

    private SizingResult withheld(SizingRequest req, String symbol, String reason, List<InputEvidence> evidence) {
        List<ConstraintResult> constraints = java.util.Arrays.stream(PositionSizingV1.ConstraintCode.values())
                .map(c -> new ConstraintResult(c.name(), "WITHHELD", null, false)).toList();
        return new SizingResult("WITHHELD", symbol, req.mode() == null ? "MANUAL" : req.mode().name(), null, null,
                STANDARD_LOT, null, null, null, null, null, null, null,
                Boolean.TRUE.equals(req.costPolicy() == null ? null : req.costPolicy().excludeCosts()),
                null, null, null, null, null, null, null, null, null, null, constraints, List.copyOf(evidence),
                List.of(reason), List.of(), PositionSizingV1.RULE_VERSION, PositionSizingV1.LOT_RULE_VERSION, Instant.now(clock));
    }

    private static boolean isSupportedVenue(String venue) {
        return venue != null && (venue.equals("HOSE") || venue.equals("HNX") || venue.equals("UPCOM"));
    }
    private static void validateShape(SizingRequest request) {
        if (request.mode() == Mode.MANUAL) {
            if (request.portfolioId() != null || request.manualCapital() == null) throw invalid("INVALID_INPUT");
        } else if (request.mode() == Mode.PORTFOLIO) {
            if (request.portfolioId() == null || request.manualCapital() != null) throw invalid("INVALID_INPUT");
        } else {
            throw invalid("INVALID_INPUT");
        }

        PriceInput price = request.priceInput();
        if (price.source() == PriceSource.MANUAL) {
            if (price.entryPriceVnd() == null || price.stopPriceVnd() == null || price.strategyCode() != null
                    || price.ruleVersion() != null || price.calculatedAt() != null || price.entryBasis() != null
                    || price.confirmed() != null) throw invalid("INVALID_INPUT");
        } else if (price.source() == PriceSource.SIGNAL) {
            if (!Boolean.TRUE.equals(price.confirmed())) throw invalid("SIGNAL_NOT_CONFIRMED");
            if (price.strategyCode() == null || price.ruleVersion() == null || price.calculatedAt() == null
                    || price.entryBasis() == null || price.entryPriceVnd() != null || price.stopPriceVnd() != null
                    || price.originatingSignalContext() != null) {
                throw invalid("INVALID_INPUT");
            }
        } else {
            throw invalid("INVALID_INPUT");
        }

        CostPolicy cost = request.costPolicy();
        boolean anyCost = hasAnyCost(cost);
        boolean allCosts = cost.entryFeeRate() != null && cost.exitFeeRate() != null && cost.sellTaxRate() != null
                && cost.entrySlippageRate() != null && cost.exitSlippageRate() != null;
        if ((cost.excludeCosts() && anyCost) || (!cost.excludeCosts() && !allCosts)) {
            throw invalid("INCOMPLETE_COST_POLICY");
        }
        if (request.exposureLimits() != null
                && request.exposureLimits().maxSymbolConcentrationRate() == null
                && request.exposureLimits().maxDeploymentRate() == null) throw invalid("INVALID_INPUT");
    }
    private static boolean hasAnyCost(CostPolicy c) { return c.entryFeeRate() != null || c.exitFeeRate() != null
            || c.sellTaxRate() != null || c.entrySlippageRate() != null || c.exitSlippageRate() != null; }
    private static boolean allCostsZero(CostPolicy c) {
        return decimal(c.entryFeeRate(), "entryFeeRate").signum() == 0
                && decimal(c.exitFeeRate(), "exitFeeRate").signum() == 0
                && decimal(c.sellTaxRate(), "sellTaxRate").signum() == 0
                && decimal(c.entrySlippageRate(), "entrySlippageRate").signum() == 0
                && decimal(c.exitSlippageRate(), "exitSlippageRate").signum() == 0;
    }
    private static BigDecimal decimal(String value, String field) {
        if (value == null || !value.matches("^(0|[1-9][0-9]*)(\\.[0-9]+)?$")) throw invalid("INVALID_INPUT");
        try { return new BigDecimal(value); } catch (NumberFormatException ex) { throw invalid("INVALID_INPUT"); }
    }
    private static BigDecimal optionalDecimal(String value, String field) { return value == null ? null : decimal(value, field); }
    private static InvalidSizingRequestException invalid(String code) { return new InvalidSizingRequestException(code); }
    private static String publicValidationReason(String internal) {
        if (internal == null) return "INVALID_INPUT";
        if (internal.contains("STOP") || internal.contains("LOSS_PER_SHARE")) return "INVALID_PRICE_RELATIONSHIP";
        if (internal.contains("RISK")) return "INVALID_RISK_BUDGET";
        return "INVALID_INPUT";
    }
    private static String s(BigDecimal value) { return value == null ? null : value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString(); }
    private static void addEvidence(List<InputEvidence> list, String field, BigDecimal value, String source,
            String unit, Instant asOf, String key) { if (value != null) list.add(new InputEvidence(field, s(value), source, unit, asOf, key)); }
}
