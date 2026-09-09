package com.vetos.modules.integration.efatura.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * faturaentegrator.com'un callback_url'ine POST ettigi bildirim govdesi.
 * Durum bilgisi ICERMEZ -- sadece hangi faturanin (invoice_id) degistigini
 * ve bunun gercekten faturaentegrator'dan geldigini dogrulamaya yarayan
 * bir imza (hash) tasir. Bkz. FaturaEntegratorCallbackController.
 */
public record FaturaEntegratorCallbackRequest(
    @JsonProperty("invoice_id") long invoiceId,
    @JsonProperty("team_id") long teamId,
    @JsonProperty("time") String time,
    @JsonProperty("hash") String hash
) {}
