package com.vetos.platform.quota;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyQuotaExceededExceptionTest {

    @Test
    void should_buildAiMessage() {
        var ex = new MonthlyQuotaExceededException(UsageKind.AI, 100, 100, 1);
        assertThat(ex.getErrorCode()).isEqualTo("MONTHLY_QUOTA_EXCEEDED");
        assertThat(ex.getMessage()).isEqualTo("Bu ayki AI hakkınız doldu (100/100). Paketinizi yükseltmek için yöneticinize başvurun.");
    }

    @Test
    void should_buildMessageMessage_withRemainingNeverNegative() {
        assertThat(new MonthlyQuotaExceededException(UsageKind.MESSAGE, 1240, 1000, 5).getMessage())
            .isEqualTo("Bu ayki mesaj hakkınız yetersiz: 5 alıcı, kalan 0.");
    }
}
