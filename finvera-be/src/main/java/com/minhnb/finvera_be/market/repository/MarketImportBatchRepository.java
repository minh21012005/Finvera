package com.minhnb.finvera_be.market.repository;
import com.minhnb.finvera_be.market.entity.MarketImportBatchEntity; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface MarketImportBatchRepository extends JpaRepository<MarketImportBatchEntity,UUID>{ boolean existsByPackageSha256(String packageSha256); }
