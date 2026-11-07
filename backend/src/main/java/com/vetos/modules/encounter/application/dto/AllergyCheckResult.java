package com.vetos.modules.encounter.application.dto;

import com.vetos.modules.encounter.domain.DrugAllergyMatcher;

import java.util.List;

/** criticalAlert bossa onay gerekmez; matches doluysa gerekce zorunludur. */
public record AllergyCheckResult(String criticalAlert, List<DrugAllergyMatcher.Match> matches) {}
