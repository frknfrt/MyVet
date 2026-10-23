package com.vetos.modules.platformadmin.domain;

/** Render edilmis konu + HTML govde -- onizleme ve gonderim ayni cikti tipini paylasir. */
public record RenderedEmail(String subject, String html) {
}
