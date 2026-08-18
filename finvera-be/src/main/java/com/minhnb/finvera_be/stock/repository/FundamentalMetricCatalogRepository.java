package com.minhnb.finvera_be.stock.repository;

import com.minhnb.finvera_be.stock.entity.FundamentalMetricCatalogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundamentalMetricCatalogRepository
        extends JpaRepository<FundamentalMetricCatalogEntity, FundamentalMetricCatalogEntity.Key> {

    List<FundamentalMetricCatalogEntity> findByCatalogVersion(String catalogVersion);
}
