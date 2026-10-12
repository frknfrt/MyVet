package com.vetos.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Async icin paylasilan, sinirli boyutlu executor -- notification, tarbil
 * ve e-fatura outbox executor'lari (NotificationSendExecutor,
 * TarbilSyncExecutor, EInvoiceSubmissionExecutor) hicbir qualifier
 * belirtmedigi icin hepsi bu bean'i kullanir. Kuyruk dolarsa cagiran
 * thread'in kendisi gorevi calistirir (CallerRunsPolicy) -- is kaybolmaz,
 * bunun yerine cagiran istek dogal olarak yavaslar (geri basinc).
 * MdcTaskDecorator ile requestId/tenantId log korelasyonu async thread'e tasinir.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-outbox-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        // Kapanista (deploy/restart) kuyruktaki outbox gonderimleri yarida
        // kesilmesin; kalanlari zaten retry zamanlayicilari toplar, ama yarim
        // kalmis bir dis cagri "gonderildi mi?" belirsizligi yaratir.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
