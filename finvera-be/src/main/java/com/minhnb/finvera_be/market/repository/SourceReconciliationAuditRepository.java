package com.minhnb.finvera_be.market.repository;

import com.minhnb.finvera_be.market.entity.SourceReconciliationAuditEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceReconciliationAuditRepository extends JpaRepository<SourceReconciliationAuditEntity, UUID> {
    Optional<SourceReconciliationAuditEntity>
            findByTcbsIngestionRecordIdAndVnstockIngestionRecordIdAndPolicyVersion(
                    UUID tcbsIngestionRecordId, UUID vnstockIngestionRecordId, String policyVersion);
}
