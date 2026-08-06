package com.vetos.modules.lab.application.dto;

import com.vetos.modules.lab.domain.LabValueFlag;

import java.util.UUID;

public record LabResultItemDetail(UUID id, String parameterName, String value, String unit, String referenceRange, LabValueFlag flag) {}
