package com.vetos.modules.consentform.domain;

public interface ConsentFormPdfPort {
    byte[] render(ConsentFormPdfData data);
}
