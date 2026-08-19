package com.minhnb.finvera_be.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture rules enforcing boundaries for the {@code portfolio} module.
 *
 * <p>The {@code portfolio} module MUST NOT reach directly into {@code market.entity},
 * {@code market.repository}, {@code stock.entity}, or {@code stock.repository}.
 * Cross-module reads must use published interfaces ({@code MarketReferenceDataService},
 * {@code StockReferenceDataService}).
 */
@AnalyzeClasses(packages = "com.minhnb.finvera_be", importOptions = ImportOption.DoNotIncludeTests.class)
public class PortfolioModuleArchitectureTests {

    @ArchTest
    public static final ArchRule PORTFOLIO_DOES_NOT_REACH_INTO_MARKET_PERSISTENCE = noClasses()
            .that().resideInAPackage("..portfolio..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..market.entity..",
                    "..market.repository..");

    @ArchTest
    public static final ArchRule PORTFOLIO_DOES_NOT_REACH_INTO_STOCK_PERSISTENCE = noClasses()
            .that().resideInAPackage("..portfolio..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..stock.entity..",
                    "..stock.repository..");

    @ArchTest
    public static final ArchRule PORTFOLIO_DOMAIN_STAYS_FRAMEWORK_AND_ADAPTER_FREE = noClasses()
            .that().resideInAPackage("..portfolio.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "..portfolio.controller..",
                    "..portfolio.dto..",
                    "..portfolio.service..",
                    "..portfolio.repository..",
                    "..portfolio.entity..",
                    "..market.entity..",
                    "..market.repository..",
                    "..stock.entity..",
                    "..stock.repository..",
                    "org.apache.kafka..",
                    "io.lettuce..",
                    "org.redisson..",
                    "io.qdrant..",
                    "..ai..");

    @ArchTest
    public static final ArchRule PORTFOLIO_CONTROLLERS_DO_NOT_BYPASS_SERVICES = noClasses()
            .that().resideInAPackage("..portfolio.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..portfolio.repository..",
                    "..portfolio.entity..",
                    "..market.repository..",
                    "..market.entity..",
                    "..stock.repository..",
                    "..stock.entity..")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule PORTFOLIO_ENTITIES_DO_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that().resideInAPackage("..portfolio.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..portfolio.controller..",
                    "..portfolio.dto..",
                    "..portfolio.service..",
                    "..portfolio.repository..");
}
