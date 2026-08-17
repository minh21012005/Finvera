package com.minhnb.finvera_be.market.service;

import com.minhnb.finvera_be.market.domain.reconciliation.SourceReconciliationPolicy;
import com.minhnb.finvera_be.market.domain.reconciliation.SourceReconciliationPolicy.Decision;
import com.minhnb.finvera_be.market.entity.SourceReconciliationAuditEntity;
import com.minhnb.finvera_be.market.repository.SourceReconciliationAuditRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records only reconciliation outcomes that must withhold downstream calculations. */
@Service
public class SourceReconciliationService {
    private final SourceReconciliationPolicy policy = new SourceReconciliationPolicy();
    private final SourceReconciliationAuditRepository audits;
    private final Clock clock;

    public SourceReconciliationService(SourceReconciliationAuditRepository audits, Clock clock) {
        this.audits = audits;
        this.clock = clock;
    }

    @Transactional
    public Result reconcile(Command command) {
        Objects.requireNonNull(command, "command");
        Decision decision = policy.reconcile(command.tcbsFact(), command.vnstockFact());
        if (decision != Decision.SOURCE_CONFLICT && decision != Decision.NON_COMPARABLE) {
            return new Result(decision, Optional.empty());
        }
        SourceReconciliationAuditEntity audit = audits
                .findByTcbsIngestionRecordIdAndVnstockIngestionRecordIdAndPolicyVersion(
                        command.tcbsIngestionRecordId(), command.vnstockIngestionRecordId(),
                        SourceReconciliationPolicy.VERSION)
                .orElseGet(() -> audits.save(new SourceReconciliationAuditEntity(UUID.randomUUID(),
                        command.instrumentId(), command.tradingDate(), command.tcbsIngestionRecordId(),
                        command.vnstockIngestionRecordId(), decision.name(), SourceReconciliationPolicy.VERSION,
                        clock.instant())));
        return new Result(decision, Optional.of(audit.getId()));
    }

    public record Command(UUID instrumentId, LocalDate tradingDate, UUID tcbsIngestionRecordId,
            UUID vnstockIngestionRecordId, SourceReconciliationPolicy.Fact tcbsFact,
            SourceReconciliationPolicy.Fact vnstockFact) {
        public Command {
            Objects.requireNonNull(instrumentId, "instrumentId");
            Objects.requireNonNull(tradingDate, "tradingDate");
            Objects.requireNonNull(tcbsIngestionRecordId, "tcbsIngestionRecordId");
            Objects.requireNonNull(vnstockIngestionRecordId, "vnstockIngestionRecordId");
            Objects.requireNonNull(tcbsFact, "tcbsFact");
            Objects.requireNonNull(vnstockFact, "vnstockFact");
        }
    }

    public record Result(Decision decision, Optional<UUID> auditId) { }
}
