package com.vetos.modules.ai.domain;

import java.util.List;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). Her eleman bir dosyanin ham bayti + content-type'idir. */
public record ImagingFindingsOcrInput(List<OcrImageInput> images) {}
