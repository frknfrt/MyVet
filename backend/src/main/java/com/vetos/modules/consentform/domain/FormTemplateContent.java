package com.vetos.modules.consentform.domain;

import java.util.List;

public record FormTemplateContent(String title, List<FormSection> sections,
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    PrivacyNoticeAcknowledgement privacyNotice,
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
    boolean privacyOptionsEnabled) {
    public FormTemplateContent(String title, List<FormSection> sections) { this(title, sections, null, false); }
}
