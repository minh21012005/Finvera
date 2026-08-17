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
                    "..market.api..",
                    "..market.infrastructure..",
                    "org.apache.kafka..",
                    "io.lettuce..",
                    "org.redisson..",
                    "io.qdrant..",
                    "..ai..");
}
