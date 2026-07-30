/**
 * Modulith not: bu paket diger moduller icin acik (NamedInterface) --
 * ama sadece *LookupPort arayuzleri kullanilmali. Tam CRUD repository'ler
 * (StaffUserRepository, TenantRepository...) SADECE bu modul icinde
 * kullanilir; bu kural derleme zamaninda degil kod incelemesiyle
 * korunur (@docs/reference-module.md Bolum 12).
 */
@org.springframework.modulith.NamedInterface("domain")
package com.vetos.modules.tenant.domain;
