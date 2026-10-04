package com.vetos.modules.integration.tarbil.application.dto;

import java.util.UUID;

public record TarbilDiseaseSummary(UUID id, UUID parentId, String name, String path, boolean selectable) {}
