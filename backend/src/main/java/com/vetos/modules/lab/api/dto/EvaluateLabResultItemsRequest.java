package com.vetos.modules.lab.api.dto;

import jakarta.validation.Valid;

import java.util.List;

public record EvaluateLabResultItemsRequest(@Valid List<LabResultItemRequest> items) {}
