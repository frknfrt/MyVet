package com.vetos.platform.quota;

/** enforced=false: kisitlama kapali, sayim var ama engel yok. Item.quota null = sinirsiz. */
public record UsageSnapshot(String period, boolean enforced, Item ai, Item message) {

    public record Item(int used, Integer quota) {}
}
