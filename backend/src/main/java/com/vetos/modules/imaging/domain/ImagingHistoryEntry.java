package com.vetos.modules.imaging.domain;

import java.time.Instant;

/** AI önerisi için tamamlanmış görüntüleme (spec 2026-10-25-ai-hasta-gecmisi). date = sonuç tarihi, yoksa istek tarihi. */
public record ImagingHistoryEntry(ImagingModality modality, String bodyRegion, Instant date, String findings) {}
