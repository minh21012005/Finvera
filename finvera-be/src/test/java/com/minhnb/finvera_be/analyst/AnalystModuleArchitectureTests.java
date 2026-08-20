package com.minhnb.finvera_be.analyst;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture rules and isolation tests for the {@code analyst} module.
 */
@AnalyzeClasses(packages = "com.minhnb.finvera_be", importOptions = ImportOption.DoNotIncludeTests.class)
public class AnalystModuleArchitectureTests {

    @ArchTest
    public static final ArchRule ANALYST_DOES_NOT_REACH_INTO_MARKET_PERSISTENCE = noClasses()
            .that().resideInAPackage("..analyst..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..market.entity..",
                    "..market.repository..");

    @ArchTest
    public static final ArchRule ANALYST_DOES_NOT_REACH_INTO_STOCK_PERSISTENCE = noClasses()
            .that().resideInAPackage("..analyst..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..stock.entity..",
                    "..stock.repository..");

    @ArchTest
    public static final ArchRule ANALYST_DOES_NOT_REACH_INTO_PORTFOLIO_PERSISTENCE = noClasses()
            .that().resideInAPackage("..analyst..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..portfolio.entity..",
                    "..portfolio.repository..");

    @ArchTest
    public static final ArchRule ANALYST_DOES_NOT_REACH_INTO_RESEARCH_PERSISTENCE = noClasses()
            .that().resideInAPackage("..analyst..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..research.entity..",
                    "..research.repository..");

    @ArchTest
    public static final ArchRule ANALYST_CONTROLLERS_DO_NOT_BYPASS_SERVICES = noClasses()
            .that().resideInAPackage("..analyst.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..analyst.repository..",
                    "..analyst.entity..")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule ANALYST_ENTITIES_DO_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that().resideInAPackage("..analyst.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..analyst.controller..",
                    "..analyst.dto..",
                    "..analyst.service..");
}
