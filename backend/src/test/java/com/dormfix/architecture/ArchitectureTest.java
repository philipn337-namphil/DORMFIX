package com.dormfix.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {
    private final JavaClasses production = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.dormfix");

    @Test
    void apiCannotReachPersistenceOrBusinessEntities() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..", "..domain..", "org.springframework.data..", "jakarta.persistence..")
                .check(production);
    }

    @Test
    void domainRemainsIndependent() {
        // No business domain classes in Phase 0; rule activates on the first domain slice.
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..",
                        "..infrastructure..", "org.springframework..", "jakarta.servlet..", "software.amazon..")
                .allowEmptyShould(true).check(production);
    }

    @Test
    void applicationUsesBoundaryContracts() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..",
                        "org.springframework.data.jpa..", "jakarta.persistence..",
                        "jakarta.servlet..", "software.amazon..")
                .allowEmptyShould(true).check(production);
    }

    @Test
    void controllersCannotOwnTransactions() {
        noClasses().that().resideInAPackage("..api..")
                .should().beAnnotatedWith(Transactional.class).check(production);
        noMethods().that().areDeclaredInClassesThat().resideInAPackage("..api..")
                .should().beAnnotatedWith(Transactional.class).check(production);
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.transaction..", "jakarta.transaction..").check(production);
    }

    @Test
    void platformDoesNotDependOnFeatures() {
        noClasses().that().resideInAPackage("..platform..")
                .should().dependOnClassesThat().resideInAnyPackage("com.dormfix.identity..",
                        "com.dormfix.location..", "com.dormfix.catalog..", "com.dormfix.maintenance..",
                        "com.dormfix.notification..").check(production);
    }

    @Test
    void featurePackagesHaveNoCycles() {
        slices().matching("com.dormfix.(*)..").should().beFreeOfCycles().check(production);
    }
}
