package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.domain.model.MarketTypes.DataStatus;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.FactorDirection;
import com.minhnb.finvera_be.market.domain.model.MarketTypes.RegimeLabel;
import com.minhnb.finvera_be.market.domain.regime.MarketRegimeV1;
import com.minhnb.finvera_be.market.domain.regime.RegimeAssessment;
import com.minhnb.finvera_be.market.entity.MarketRegimeAssessmentEntity;
import com.minhnb.finvera_be.market.entity.MarketRegimeAssessmentInputEntity;
import com.minhnb.finvera_be.market.entity.MarketRegimeFactorEntity;
import com.minhnb.finvera_be.market.repository.RegimeAssessmentInputRepository;
import com.minhnb.finvera_be.market.repository.RegimeAssessmentRepository;
import com.minhnb.finvera_be.market.repository.RegimeFactorRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for immutable, source-reconciled regime assessments. */
@Service
public class RegimeAssessmentService {

    private final RegimeAssessmentRepository assessments;
    private final RegimeAssessmentInputRepository inputs;
    private final RegimeFactorRepository factors;
    private final Clock clock;

    public RegimeAssessmentService(RegimeAssessmentRepository assessments,
            RegimeAssessmentInputRepository inputs, RegimeFactorRepository factors, Clock clock) {
        this.assessments = assessments;
        this.inputs = inputs;
        this.factors = factors;
        this.clock = clock;
    }

    @Transactional
    public StoredAssessment persist(AssessmentCommand command) {
        Objects.requireNonNull(command, "command");
        RegimeAssessment assessment = containsSourceConflict(command.sourceValues())
                ? withheldForSourceConflict(command.assessment()) : command.assessment();
        UUID id = UUID.randomUUID();
        Instant calculatedAt = clock.instant();
        assessments.save(toEntity(id, command, assessment, calculatedAt));
        inputs.saveAll(command.inputLinks().stream().map(link -> toInputEntity(id, link)).toList());
        factors.saveAll(assessment.factors().stream()
                .map(factor -> new MarketRegimeFactorEntity(UUID.randomUUID(), id, factor.component().name(),
                        factor.direction().name(), factor.normalizedScore(), factor.originalWeight(),
                        factor.effectiveWeight(), factor.contribution()))
                .toList());
        return new StoredAssessment(id, command.tradingDate(), command.asOf(), assessment, command.supersedesAssessmentId());
    }

    @Transactional(readOnly = true)
    public Optional<Snapshot> latestFor(LocalDate tradingDate) {
        return assessments.findFirstByTradingDateOrderByAsOfDescCalculatedAtDesc(tradingDate)
                .map(entity -> new Snapshot(entity.getTradingDate(), entity.getAsOf(), entity.getRuleVersion(),
                        fromEntity(entity, factors.findByAssessmentIdOrderByFactorCode(entity.getId()))));
    }

    private static boolean containsSourceConflict(List<SourceValue> sourceValues) {
        Map<String, BigDecimal> canonicalValueBySubject = new HashMap<>();
        for (SourceValue sourceValue : sourceValues) {
            BigDecimal existing = canonicalValueBySubject.putIfAbsent(sourceValue.subject(), sourceValue.value());
            if (existing != null && existing.compareTo(sourceValue.value()) != 0) {
                return true;
            }
        }
        return false;
    }

    private static RegimeAssessment withheldForSourceConflict(RegimeAssessment candidate) {
        List<String> reasons = new ArrayList<>(candidate.reasonCodes());
        if (!reasons.contains("SOURCE_CONFLICT")) {
            reasons.add("SOURCE_CONFLICT");
        }
        return new RegimeAssessment(DataStatus.PARTIAL, null, null, null, candidate.completeness(), null,
                null, false, reasons, List.of());
    }

    private static MarketRegimeAssessmentEntity toEntity(UUID id, AssessmentCommand command,
            RegimeAssessment assessment, Instant calculatedAt) {
        return new MarketRegimeAssessmentEntity(id, command.tradingDate(), command.asOf(), calculatedAt,
                MarketRegimeV1.RULE_VERSION, assessment.label() == null ? null : assessment.label().name(),
                assessment.score(), assessment.confidence(), assessment.dataStatus().name(), assessment.completeness(),
                assessment.factorAgreement(), assessment.boundaryDistance(), assessment.renormalized(),
                assessment.reasonCodes(), command.supersedesAssessmentId());
    }

    private static MarketRegimeAssessmentInputEntity toInputEntity(UUID assessmentId, InputLink inputLink) {
        return new MarketRegimeAssessmentInputEntity(assessmentId, inputLink.inputRole(), inputLink.indexSnapshotId(),
                inputLink.breadthSnapshotId(), inputLink.priceObservationId(), inputLink.inputSetHash());
    }

    private static RegimeAssessment fromEntity(MarketRegimeAssessmentEntity assessment,
            List<MarketRegimeFactorEntity> factors) {
        List<RegimeAssessment.SupportingFactor> supportingFactors = factors.stream()
                .map(factor -> new RegimeAssessment.SupportingFactor(
                        MarketRegimeV1.Component.valueOf(factor.getFactorCode()),
                        FactorDirection.valueOf(factor.getDirection()), factor.getNormalizedScore(), factor.getWeight(),
                        factor.getEffectiveWeight(), factor.getContribution()))
                .toList();
        return new RegimeAssessment(DataStatus.valueOf(assessment.getDataStatus()),
                assessment.getLabel() == null ? null : RegimeLabel.valueOf(assessment.getLabel()), assessment.getScore(),
                assessment.getConfidence(), assessment.getCompleteness(), assessment.getFactorAgreement(),
                assessment.getBoundaryDistance(), assessment.isRenormalized(), assessment.getReasonCodes(), supportingFactors);
    }

    public record AssessmentCommand(LocalDate tradingDate, Instant asOf, RegimeAssessment assessment,
                                    UUID supersedesAssessmentId, List<InputLink> inputLinks,
                                    List<SourceValue> sourceValues) {
        public AssessmentCommand {
            Objects.requireNonNull(tradingDate, "tradingDate");
            Objects.requireNonNull(asOf, "asOf");
            Objects.requireNonNull(assessment, "assessment");
            inputLinks = List.copyOf(inputLinks);
            sourceValues = List.copyOf(sourceValues);
        }
    }

    public record InputLink(String inputRole, UUID indexSnapshotId, UUID breadthSnapshotId,
                            UUID priceObservationId, String inputSetHash) {
        public InputLink {
            Objects.requireNonNull(inputRole, "inputRole");
            int targetCount = (indexSnapshotId == null ? 0 : 1) + (breadthSnapshotId == null ? 0 : 1)
                    + (priceObservationId == null ? 0 : 1) + (inputSetHash == null ? 0 : 1);
            if (targetCount != 1) throw new IllegalArgumentException("exactly one regime input target is required");
        }
        public static InputLink indexSnapshot(String role, UUID id) { return new InputLink(role, id, null, null, null); }
        public static InputLink breadthSnapshot(String role, UUID id) { return new InputLink(role, null, id, null, null); }
        public static InputLink priceObservation(String role, UUID id) { return new InputLink(role, null, null, id, null); }
        public static InputLink inputSet(String role, String hash) { return new InputLink(role, null, null, null, hash); }
    }

    public record SourceValue(String source, String subject, BigDecimal value) {
        public SourceValue {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(value, "value");
        }
    }

    public record StoredAssessment(UUID id, LocalDate tradingDate, Instant asOf, RegimeAssessment assessment,
                                   UUID supersedesAssessmentId) { }
    public record Snapshot(LocalDate tradingDate, Instant asOf, String ruleVersion, RegimeAssessment assessment) { }
}
