package com.vetos.modules.lab.application.dto;

import com.vetos.modules.lab.domain.LabValueFlag;

public record LabResultItemInput(String parameterName, String value, String unit, String referenceRange, LabValueFlag flag) {}
