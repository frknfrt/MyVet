package com.vetos.platform.quota;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** TRIAL'in plans tablosunda satiri yok (kod ismen taniyor) -- deneme kotalari burada. */
@ConfigurationProperties("vetly.quota")
public record QuotaProperties(@DefaultValue Trial trial) {

    public record Trial(@DefaultValue("30") int ai, @DefaultValue("100") int message) {}
}
