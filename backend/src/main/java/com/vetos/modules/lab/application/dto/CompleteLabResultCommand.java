package com.vetos.modules.lab.application.dto;

import java.util.List;
import java.util.UUID;

public record CompleteLabResultCommand(UUID labResultId, String resultSummary, List<LabResultItemInput> items) {}
