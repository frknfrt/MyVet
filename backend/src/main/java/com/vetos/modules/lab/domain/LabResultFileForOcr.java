package com.vetos.modules.lab.domain;

import java.util.UUID;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). */
public record LabResultFileForOcr(UUID id, String fileName, String contentType, byte[] content) {}
