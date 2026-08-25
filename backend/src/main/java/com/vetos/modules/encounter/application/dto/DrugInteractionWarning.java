package com.vetos.modules.encounter.application.dto;

import java.util.UUID;

public record DrugInteractionWarning(UUID drugAId, String drugAName, UUID drugBId, String drugBName) {}
