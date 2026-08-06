package com.vetos.modules.imaging.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ImagingRecordFileMeta(UUID id, String fileName, String contentType, long fileSize, Instant uploadedAt) {}
