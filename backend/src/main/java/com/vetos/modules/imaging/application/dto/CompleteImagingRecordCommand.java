package com.vetos.modules.imaging.application.dto;

import java.util.UUID;

public record CompleteImagingRecordCommand(UUID imagingRecordId, String findings) {}
