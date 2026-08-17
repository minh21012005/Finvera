package com.minhnb.finvera_be.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.minhnb.finvera_be.market")
class MarketModuleArchitectureTests {

    @ArchTest
    static final ArchRule DOMAIN_STAYS_FRAMEWORK_AND_ADAPTER_FREE = noClasses()
            .that().resideInAPackage("..market.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "..market.controller..",
                    "..market.dto..",
                    "..market.service..",
                    "..market.repository..",
                    "..market.entity..",
                    "..market.provider..",
                    "org.apache.kafka..",
                    "io.lettuce..",
                    "org.redisson..",
                    "io.qdrant..",
                    "..ai..");

    @ArchTest
    static final ArchRule CONTROLLERS_DO_NOT_BYPASS_SERVICES = noClasses()
            .that().resideInAPackage("..market.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..market.repository..",
                    "..market.entity..");

    @ArchTest
    static final ArchRule ENTITIES_DO_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that().resideInAPackage("..market.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..market.controller..",
                    "..market.dto..",
                    "..market.service..",
                    "..market.repository..",
                    "..market.provider..");
}
