package com.vetos.modules.platformadmin.domain;

/**
 * Bir e-posta sablonundaki tek bir {{TOKEN}} alani -- key render/validasyonda
 * kullanilir, label admin panelindeki formun etiketidir.
 */
public record EmailTemplateField(String key, String label) {
}
