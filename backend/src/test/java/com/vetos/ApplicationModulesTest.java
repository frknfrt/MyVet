package com.vetos;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Modul sinir ihlali olursa build kirilir (architecture.md Bolum 1/6).
 * com.vetos.modules altindaki her modul, com.vetos.platform disinda
 * baska bir modulun domain/infrastructure sinifina dogrudan erisemez.
 */
class ApplicationModulesTest {

    @Test
    void billingUsesPatientPortsForPersistenceAccess() {
        var classes = new com.tngtech.archunit.core.importer.ClassFileImporter()
            .importPackages("com.vetos.modules.billing", "com.vetos.modules.patient");
        var billing = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
            .that().resideInAPackage("com.vetos.modules.billing..");
        var patientDomain = com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("com.vetos.modules.patient.domain..");
        var repositories = com.tngtech.archunit.base.DescribedPredicate.<com.tngtech.archunit.core.domain.JavaClass>describe(
            "patient entities or repositories", type -> type.getSimpleName().endsWith("Repository")
                || type.isAnnotatedWith(jakarta.persistence.Entity.class));
        billing.should().dependOnClassesThat(patientDomain.and(repositories)).check(classes);
    }

    @Test
    void verifyModuleBoundaries() {
        ApplicationModules.of(VetosApplication.class).verify();
    }
}
