package com.minhnb.finvera_be.research;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.auth.config.OwnerProperties;
import com.minhnb.finvera_be.research.service.OwnerScopedResearchAccess;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules and ownership scoping tests for the {@code research} module.
 */
@AnalyzeClasses(packages = "com.minhnb.finvera_be", importOptions = ImportOption.DoNotIncludeTests.class)
public class ResearchModuleArchitectureTests {

    @ArchTest
    public static final ArchRule RESEARCH_DOES_NOT_REACH_INTO_MARKET_PERSISTENCE = noClasses()
            .that().resideInAPackage("..research..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..market.entity..",
                    "..market.repository..");

    @ArchTest
    public static final ArchRule RESEARCH_DOES_NOT_REACH_INTO_STOCK_PERSISTENCE = noClasses()
            .that().resideInAPackage("..research..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..stock.entity..",
                    "..stock.repository..");

    @ArchTest
    public static final ArchRule RESEARCH_DOES_NOT_REACH_INTO_PORTFOLIO_PERSISTENCE = noClasses()
            .that().resideInAPackage("..research..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..portfolio.entity..",
                    "..portfolio.repository..");

    @ArchTest
    public static final ArchRule RESEARCH_CONTROLLERS_DO_NOT_BYPASS_SERVICES = noClasses()
            .that().resideInAPackage("..research.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..research.repository..",
                    "..research.entity..",
                    "..market.repository..",
                    "..market.entity..",
                    "..stock.repository..",
                    "..stock.entity..")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule RESEARCH_ENTITIES_DO_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that().resideInAPackage("..research.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..research.controller..",
                    "..research.dto..",
                    "..research.service..",
                    "..research.repository..");

    @Test
    void ownerScopedAccessTreatsWrongOwnerAndNonexistentIdentically() {
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        OwnerProperties properties = new OwnerProperties(ownerId, "owner", "pass");
        OwnerScopedResearchAccess access = new OwnerScopedResearchAccess(properties);

        assertThat(access.getAuthenticatedOwnerId()).isEqualTo(ownerId);
        assertThat(access.isCurrentOwner(ownerId)).isTrue();
        assertThat(access.isCurrentOwner(otherOwnerId)).isFalse();
        assertThat(access.isCurrentOwner(null)).isFalse();

        // Nonexistent resource -> empty
        Optional<String> emptyResult = access.filterOwned(Optional.empty(), s -> ownerId);
        assertThat(emptyResult).isEmpty();

        // Wrong owner resource -> empty (identical to nonexistent)
        Optional<String> wrongOwnerResult = access.filterOwned(Optional.of("secret-doc"), s -> otherOwnerId);
        assertThat(wrongOwnerResult).isEmpty();

        // Correct owner resource -> present
        Optional<String> correctOwnerResult = access.filterOwned(Optional.of("my-doc"), s -> ownerId);
        assertThat(correctOwnerResult).contains("my-doc");
    }
}
