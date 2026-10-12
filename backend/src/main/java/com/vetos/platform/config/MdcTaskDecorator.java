package com.vetos.platform.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * @Async isler HTTP isteginin thread'inden farkli bir thread'de calisir; MDC
 * thread-local oldugu icin requestId/tenantId kaybolur ve outbox loglari
 * (bildirim, e-fatura, TARBIL) tetikleyen istekle eslestirilemez. Gorev
 * gonderilirken MDC kopyalanir, calisirken kurulur, bitince calisan thread'in
 * ONCEKI MDC'si geri yuklenir -- CallerRunsPolicy gorevi istegin kendi
 * thread'inde calistirdiginda istegin MDC'si bozulmasin, havuz thread'i de
 * bir sonraki goreve eski deger tasimasin diye.
 *
 * Sadece MDC tasinir; TenantContext bilerek TASINMAZ -- executor'lar kiraciyi
 * outbox kaydindan acikca kurar (bkz. EInvoiceSubmissionExecutor "kopruleme kurali").
 */
class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> submitterContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            setOrClear(submitterContext);
            try {
                runnable.run();
            } finally {
                setOrClear(previousContext);
            }
        };
    }

    private static void setOrClear(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
