package com.vetos.modules.integration.tarbil.infrastructure.adapter;

import com.vetos.modules.integration.tarbil.application.RetryTarbilSyncUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TarbilAdminAdapterTest {

    @Mock private RetryTarbilSyncUseCase retryTarbilSyncUseCase;

    @Test
    void should_delegateToExecuteAsAdmin_when_retryNow() {
        TarbilAdminAdapter adapter = new TarbilAdminAdapter(retryTarbilSyncUseCase);
        UUID logId = UUID.randomUUID();

        adapter.retryNow(logId);

        verify(retryTarbilSyncUseCase).executeAsAdmin(logId);
    }
}
