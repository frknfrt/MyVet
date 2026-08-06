package com.vetos.modules.imaging.api.dto;

import com.vetos.modules.imaging.domain.ImagingModality;

import java.util.UUID;

public record ImagingRecordResponse(UUID id, ImagingModality modality) {}
