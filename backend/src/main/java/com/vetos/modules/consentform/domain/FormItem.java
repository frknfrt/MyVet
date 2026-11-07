package com.vetos.modules.consentform.domain;

import java.util.List;

/** Alan sirasi JSON sirasidir (degistirilmemeli). binding anahtari FormBinding.fromKey ile dogrulanir. */
public record FormItem(
    FormItemType type,
    String code,
    String label,
    String text,
    boolean required,
    String binding,
    String unit,
    List<String> options,
    boolean multiple
) {}
