package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.application.dto.AllergyCheckResult;

import java.util.List;

/** criticalAlert null ise onay gerekmez; matches doluysa gerekce zorunludur. */
public record CheckAllergiesResponse(String criticalAlert, List<Match> matches) {

    public record Match(String drugName, String reason) {}

    public static CheckAllergiesResponse from(AllergyCheckResult result) {
        return new CheckAllergiesResponse(result.criticalAlert(),
            result.matches().stream().map(m -> new Match(m.drugLabel(), m.reason())).toList());
    }
}
