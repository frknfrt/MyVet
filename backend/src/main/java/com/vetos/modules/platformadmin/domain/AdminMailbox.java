package com.vetos.modules.platformadmin.domain;

/**
 * cPanel'deki uc gercek kutu -- her birinin kendi SMTP kimlik bilgisi
 * (admin-email.<kod>.username/password) var, hepsi ayni sunucuyu (smtp-host)
 * paylasir. EmailTemplateType her sablonu dogru kutuya sabitler (ornek:
 * fatura hatirlatmasi her zaman bildirim@'dan gider).
 */
public enum AdminMailbox {
    INFO("info@vetly.com.tr"),
    DESTEK("destek@vetly.com.tr"),
    BILDIRIM("bildirim@vetly.com.tr");

    private final String address;

    AdminMailbox(String address) {
        this.address = address;
    }

    public String address() {
        return address;
    }
}
