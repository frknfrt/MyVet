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
    void verifyModuleBoundaries() {
        ApplicationModules.of(VetosApplication.class).verify();
    }
}
