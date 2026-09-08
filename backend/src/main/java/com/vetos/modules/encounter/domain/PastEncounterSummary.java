package com.vetos.modules.encounter.domain;

import java.time.Instant;

public record PastEncounterSummary(Instant date, String assessment, String plan) {}
