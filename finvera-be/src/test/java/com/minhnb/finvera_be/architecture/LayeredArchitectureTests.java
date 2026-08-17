package com.minhnb.finvera_be.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.minhnb.finvera_be")
class LayeredArchitectureTests {

    @ArchTest
    static final ArchRule CONTROLLERS_DO_NOT_BYPASS_SERVICES = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..repository..", "..entity..", "..provider..");

    @ArchTest
    static final ArchRule SERVICES_DO_NOT_DEPEND_ON_CONTROLLERS = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule DTOS_DO_NOT_DEPEND_ON_PERSISTENCE = noClasses()
            .that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..entity..");

    @ArchTest
    static final ArchRule REPOSITORIES_DO_NOT_DEPEND_ON_WEB_OR_SERVICES = noClasses()
            .that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..controller..", "..dto..", "..service..", "..provider..");
}
