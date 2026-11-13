package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.TrialSmsPort;
import com.vetos.modules.platformadmin.domain.exception.TrialSmsServiceUnavailableException;
import com.vetos.platform.observability.IntegrationOutcomes;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Deneme kaydi SMS adaptoru -- platform fatura SMS adaptoruyle ayni Ileti Merkezi hesabini
 * (ILETI_MERKEZI_* env) kullanir. Kimlik bilgisi yoksa: dev/test profilinde dogrulama kodu
 * loga yazilir (kayit akisi denenebilsin); diger ortamlarda dogrulama kodu istegi 503 ile
 * reddedilir. Bilgilendirme mesajlari (hos geldiniz, hatirlatma) her ortamda sessizce simule edilir.
 */
@Component
@Slf4j
class IletiMerkeziTrialSmsAdapter implements TrialSmsPort {

    private static final String LABEL = "Deneme kaydi SMS'i";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final IletiMerkeziSmsClient client;
    /** Odeme ve bildirim adaptorleriyle ayni kural: simulasyon yalnizca dev/test profilinde. */
    private final boolean simulationAllowed;

    @Autowired
    IletiMerkeziTrialSmsAdapter(
        @Value("${platform-billing.ileti-merkezi.api-key:}") String apiKey,
        @Value("${platform-billing.ileti-merkezi.hash:}") String hash,
        @Value("${platform-billing.ileti-merkezi.sender:vetly}") String sender,
        IntegrationOutcomes outcomes,
        @Value("#{environment.matchesProfiles('!prod & (dev | test)')}") boolean simulationAllowed
    ) {
        this(new IletiMerkeziSmsClient(apiKey, hash, sender, IletiMerkeziSmsClient.DEFAULT_SEND_URL, outcomes), simulationAllowed);
    }

    IletiMerkeziTrialSmsAdapter(IletiMerkeziSmsClient client, boolean simulationAllowed) {
        this.client = client;
        this.simulationAllowed = simulationAllowed;
    }

    @PostConstruct
    void warnIfSimulated() {
        if (!client.isConfigured()) {
            log.warn("ILETI_MERKEZI_API_KEY/HASH tanimli degil: deneme kaydi dogrulama kodu gonderilemez. {}",
                simulationAllowed
                    ? "dev/test profili: kod loga yazilir (simulasyon)."
                    : "Bu ortamda vitrindeki deneme kaydi 503 doner -- kimse kayit olamaz.");
        }
    }

    @Override
    public void sendVerificationCode(String phoneE164, String code) {
        // Kod kullaniciya ulasmayacaksa "gonderildi" denmez: canli ortamda sessiz simulasyon,
        // hic gelmeyecek bir SMS bekleyen kullanici demektir.
        if (!client.isConfigured() && !simulationAllowed) {
            throw new TrialSmsServiceUnavailableException();
        }
        client.send(phoneE164, "Vetly dogrulama kodunuz: %s. Kod 5 dakika gecerlidir.".formatted(code), LABEL);
    }

    @Override
    public void sendWelcome(String phoneE164, String clinicName, LocalDate trialEndsAt, String loginUrl) {
        client.send(phoneE164, "Vetly'ye hos geldiniz! %s icin 7 gunluk denemeniz basladi, bitis: %s. Giris: %s".formatted(
            clinicName, DATE_FORMAT.format(trialEndsAt), loginUrl), LABEL);
    }

    @Override
    public void sendTrialEndingSoon(String phoneE164, String clinicName, LocalDate trialEndsAt) {
        client.send(phoneE164, "Vetly: %s icin denemenizin bitmesine 2 gun kaldi (%s). Uyelik baslatmak icin panelinize giris yapin.".formatted(
            clinicName, DATE_FORMAT.format(trialEndsAt)), LABEL);
    }

    @Override
    public void sendTrialLastDay(String phoneE164, String clinicName) {
        client.send(phoneE164, "Vetly: %s icin denemeniz bugun sona eriyor. Kesintisiz devam etmek icin uyeliginizi baslatin.".formatted(
            clinicName), LABEL);
    }
}
