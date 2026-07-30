/**
 * Modulith not: bu paket digger moduller icin acik (NamedInterface) --
 * ama sadece *LookupPort arayuzleri kullanilmali. Tam CRUD repository'ler
 * (PatientRepository, OwnerRepository...) SADECE bu modul icinde
 * kullanilir; bu kural derleme zamaninda degil kod incelemesiyle
 * korunur (@docs/reference-module.md Bolum 12).
 */
@org.springframework.modulith.NamedInterface("domain")
package com.vetos.modules.patient.domain;
