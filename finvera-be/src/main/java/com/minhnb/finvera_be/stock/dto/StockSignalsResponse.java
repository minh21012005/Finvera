package com.minhnb.finvera_be.stock.dto;

import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.LevelSet;
import com.minhnb.finvera_be.stock.domain.strategy.StrategySignalV1.RiskFactorResult;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.SignalDetail;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.StockSignals;
import com.minhnb.finvera_be.stock.service.strategy.StrategySignalService.StrategyEvaluationResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Version 1.0 transport DTO for `GET /stocks/{symbol}/signals` (contracts/strategy-signal.openapi.yaml). */
public record StockSignalsResponse(
        String symbol,
        String dataStatus,
        List<StrategyEvaluationResponse> evaluations,
        String disclaimerCode,
        String coherenceKey,
        String asOf) {

    public static StockSignalsResponse from(StockSignals signals) {
        return new StockSignalsResponse(
                signals.symbol(),
                signals.dataStatus().name(),
                signals.evaluations().stream().map(StrategyEvaluationResponse::from).toList(),
                "QUANTITATIVE_DECISION_SUPPORT",
                signals.coherenceKey(),
                signals.asOf().toString());
    }

    public record StrategyEvaluationResponse(String strategyCode, String status, String reasonCode,
            SignalResponse signal) {
        static StrategyEvaluationResponse from(StrategyEvaluationResult result) {
            return new StrategyEvaluationResponse(result.strategyCode().name(), result.status().name(),
                    result.reasonCode(), result.signal() == null ? null : SignalResponse.from(result.signal()));
        }
    }

    public record SignalResponse(
            String strategyCode,
            String ruleVersion,
            String direction,
            String entryLow,
            String entryHigh,
            String stopLoss,
            String target1,
            String target2,
            String riskReward,
            Integer riskScore,
            String riskLevel,
            String signalStrength,
            List<RiskFactorResponse> riskFactors,
            Map<String, String> supportingEvidence,
            List<String> reasonCodes,
            String asOfTradingDate,
            String calculatedAt) {
        static SignalResponse from(SignalDetail detail) {
            LevelSet levels = detail.levels();
            return new SignalResponse(
                    detail.strategyCode().name(),
                    detail.ruleVersion(),
                    detail.direction().name(),
                    levels.entryLow().toPlainString(),
                    levels.entryHigh().toPlainString(),
                    levels.stopLoss().toPlainString(),
                    levels.target1().toPlainString(),
                    levels.target2().toPlainString(),
                    levels.riskReward().toPlainString(),
                    detail.riskScore(),
                    detail.riskLevel() == null ? null : detail.riskLevel().name(),
                    detail.signalStrength() == null ? null : detail.signalStrength().name(),
                    detail.riskFactors().stream().map(RiskFactorResponse::from).toList(),
                    detail.supportingEvidence(),
                    detail.reasonCodes(),
                    detail.asOfTradingDate().toString(),
                    detail.calculatedAt().toString());
        }
    }

    public record RiskFactorResponse(String factorCode, String inputValue, Integer factorScore, String applicability,
            String reasonCode) {
        static RiskFactorResponse from(RiskFactorResult factor) {
            BigDecimal input = factor.inputValue();
            return new RiskFactorResponse(factor.factorCode().name(), input == null ? null : input.toPlainString(),
                    factor.factorScore(), factor.applicability().name(), factor.reasonCode());
        }
    }
}
