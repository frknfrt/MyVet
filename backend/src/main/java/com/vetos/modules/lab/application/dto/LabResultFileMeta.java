package com.vetos.modules.lab.application.dto;

import java.time.Instant;
import java.util.UUID;

public record LabResultFileMeta(UUID id, String fileName, String contentType, long fileSize, Instant uploadedAt) {}
