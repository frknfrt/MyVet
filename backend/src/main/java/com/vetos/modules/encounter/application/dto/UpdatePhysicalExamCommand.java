package com.vetos.modules.encounter.application.dto;

import com.vetos.modules.encounter.domain.PhysicalExamFinding;

import java.util.List;
import java.util.UUID;

public record UpdatePhysicalExamCommand(UUID encounterId, List<PhysicalExamFinding> findings) {}
