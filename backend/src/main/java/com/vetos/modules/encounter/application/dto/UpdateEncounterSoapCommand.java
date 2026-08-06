package com.vetos.modules.encounter.application.dto;

import java.util.UUID;

public record UpdateEncounterSoapCommand(
    UUID encounterId, String subjective, String objective, String assessment, String plan, boolean aiGenerated
) {}
