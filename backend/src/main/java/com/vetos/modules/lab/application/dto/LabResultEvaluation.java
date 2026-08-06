package com.vetos.modules.lab.application.dto;

import java.util.List;

public record LabResultEvaluation(List<LabResultItemInput> items, String draftSummary) {}
