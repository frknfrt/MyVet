package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.exception.TrialSmsServiceUnavailableException;
import com.vetos.platform.observability.IntegrationOutcomes;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class IletiMerkeziTrialSmsAdapterTest {

    private IletiMerkeziTrialSmsAdapter unconfiguredAdapter(boolean simulationAllowed) {
        IletiMerkeziSmsClient client = new IletiMerkeziSmsClient("", "", "vetly", "http://localhost:1/unused", mock(IntegrationOutcomes.class));
        return new IletiMerkeziTrialSmsAdapter(client, simulationAllowed);
    }

    @Test
    void should_refuseVerificationCode_when_smsNotConfiguredOutsideDevOrTest() {
        // Canli ortamda kod loga yazilip "gonderildi" denirse kullanici hic gelmeyecek bir SMS bekler.
        assertThatThrownBy(() -> unconfiguredAdapter(false).sendVerificationCode("+905321112233", "123456"))
            .isInstanceOf(TrialSmsServiceUnavailableException.class);
    }

    @Test
    void should_simulateVerificationCode_when_smsNotConfiguredInDevOrTest() {
        assertThatCode(() -> unconfiguredAdapter(true).sendVerificationCode("+905321112233", "123456"))
            .doesNotThrowAnyException();
    }

    @Test
    void should_neverThrowForInformationalMessages_when_smsNotConfigured() {
        IletiMerkeziTrialSmsAdapter adapter = unconfiguredAdapter(false);

        assertThatCode(() -> {
            adapter.sendWelcome("+905321112233", "Pati Klinik", LocalDate.of(2026, 11, 20), "http://localhost:5173/login");
            adapter.sendTrialEndingSoon("+905321112233", "Pati Klinik", LocalDate.of(2026, 11, 20));
            adapter.sendTrialLastDay("+905321112233", "Pati Klinik");
        }).doesNotThrowAnyException();
    }

    @Test
    void should_refuseVerificationCode_when_configuredProviderRejectsSend() {
        // Kimlik bilgisi var ama saglayiciya ulasilamiyor (bakiye/onay/ag hatasi ile ayni sonuc).
        IletiMerkeziSmsClient client = new IletiMerkeziSmsClient(
            "key", "hash", "vetly", "http://127.0.0.1:1/send-sms/json", mock(IntegrationOutcomes.class));
        IletiMerkeziTrialSmsAdapter adapter = new IletiMerkeziTrialSmsAdapter(client, false);

        assertThatThrownBy(() -> adapter.sendVerificationCode("+905321112233", "123456"))
            .isInstanceOf(TrialSmsServiceUnavailableException.class);
        assertThatCode(() -> adapter.sendTrialLastDay("+905321112233", "Pati Klinik")).doesNotThrowAnyException();
    }
}
