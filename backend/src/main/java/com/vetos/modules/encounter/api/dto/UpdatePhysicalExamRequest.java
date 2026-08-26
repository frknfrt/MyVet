package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.domain.PhysicalExamFinding;

import java.util.List;

public record UpdatePhysicalExamRequest(List<PhysicalExamFinding> findings) {}
