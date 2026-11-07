package com.vetos.modules.consentform.domain;

import java.util.List;

public record FormSection(String title, List<FormItem> items) {}
