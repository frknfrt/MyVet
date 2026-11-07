package com.vetos.modules.dataimport.api.dto;

import java.util.List;

/** Eslesmesi reddedilen (yeni sahip olarak acilacak) satirlarin kaynak_id'leri; bos/yok ise hepsi eslesir. */
public record CommitImportRequest(List<String> rejectedMatchSourceIds) {
}
