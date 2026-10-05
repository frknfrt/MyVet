package com.vetos.modules.encounter.application.dto;

import java.util.List;
import java.util.UUID;

public record RecordVaccinationSeriesResult(UUID seriesId, List<UUID> vaccinationRecordIds) {}
