package com.minhnb.finvera_be.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Test sources are excluded: integration tests legitimately seed Feature 001
 * tables (market_instrument, ingestion_record) directly through their
 * repositories to satisfy foreign-key constraints, which is not the
 * production-code boundary this class polices. Production {@code stock}
 * code must still reach Feature 001 reference data only through
 * {@code MarketReferenceDataService}.
 */
@AnalyzeClasses(packages = "com.minhnb.finvera_be", importOptions = ImportOption.DoNotIncludeTests.class)
class StockModuleArchitectureTests {

    /**
     * plan.md "Cross-module rule": the stock module reads Feature 001
     * reference data only through {@code market.service.MarketReferenceDataService};
     * it must never reach into market's entities or repositories directly.
     */
    @ArchTest
    static final ArchRule STOCK_DOES_NOT_REACH_INTO_MARKET_PERSISTENCE = noClasses()
            .that().resideInAPackage("..stock..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..market.entity..",
                    "..market.repository..");

    @ArchTest
    static final ArchRule STOCK_DOMAIN_STAYS_FRAMEWORK_AND_ADAPTER_FREE = noClasses()
            .that().resideInAPackage("..stock.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "..stock.controller..",
                    "..stock.dto..",
                    "..stock.service..",
                    "..stock.repository..",
                    "..stock.entity..",
                    "..stock.provider..",
                    "..market.entity..",
                    "..market.repository..",
                    "org.apache.kafka..",
                    "io.lettuce..",
                    "org.redisson..",
                    "io.qdrant..",
                    "..ai..");

    @ArchTest
    static final ArchRule STOCK_CONTROLLERS_DO_NOT_BYPASS_SERVICES = noClasses()
            .that().resideInAPackage("..stock.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..stock.repository..",
                    "..stock.entity..",
                    "..market.repository..",
                    "..market.entity..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule STOCK_ENTITIES_DO_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that().resideInAPackage("..stock.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..stock.controller..",
                    "..stock.dto..",
                    "..stock.service..",
                    "..stock.repository..",
                    "..stock.provider..");
}
