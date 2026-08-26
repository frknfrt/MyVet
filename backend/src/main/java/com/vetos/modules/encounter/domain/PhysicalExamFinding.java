package com.vetos.modules.encounter.domain;

public record PhysicalExamFinding(ExamBodySystem system, ExamFindingStatus status, String note) {}
