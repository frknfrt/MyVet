package com.vetos.modules.lab.api.dto;

import com.vetos.modules.lab.application.dto.LabResultItemInput;
import com.vetos.modules.lab.domain.LabValueFlag;

public record EvaluatedLabItemResponse(String parameterName, String value, String unit, String referenceRange, LabValueFlag flag) {
    public static EvaluatedLabItemResponse from(LabResultItemInput i) {
        return new EvaluatedLabItemResponse(i.parameterName(), i.value(), i.unit(), i.referenceRange(), i.flag());
    }
}
