package com.vetos.modules.ai.domain;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29).
 * REVIZE (2026-10-31, Task 11 -- bkz. docs/superpowers/plans/2026-10-29-lab-
 * goruntuleme-ai-foto-okuma.md): rawImageInterpretation, AI'nin findings
 * alanini YAZILI bir rapordan degil HAM bir rontgen/USG/tomografi goruntusunden
 * gorsel olarak ciktigini isaretler. Bu true oldugunda
 * ReadImagingFindingsFromImageUseCase, findings metninin basina ZORUNLU bir
 * uyari (disclaimer) ekler -- bu ekleme DETERMINISTIK olarak Java tarafinda
 * yapilir, modelin kendi basina her zaman bir uyari yazacagina guvenilmez
 * (bkz. Task 10'daki markdown-fence guvenilirlik dersi: LLM ciktisina asla
 * tam guvenilmez, kritik davranis her zaman kod tarafinda garanti edilmeli).
 */
public record ImagingFindingsOcrDraft(String findings, boolean modelConnected, String modelVersion, boolean rawImageInterpretation) {}
