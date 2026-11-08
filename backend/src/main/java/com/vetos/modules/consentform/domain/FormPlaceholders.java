package com.vetos.modules.consentform.domain;

import java.util.regex.Pattern;

/** Şablon doğrulama ve PDF yerleştirme aynı kod/boşluk kuralını kullanır. */
public final class FormPlaceholders {
    public static final Pattern PATTERN = Pattern.compile("\\{\\{\\s*([a-z0-9_]{1,40})\\s*\\}\\}");
    private FormPlaceholders() {}
}
