package com.vetos.modules.integration.tarbil.infrastructure.adapter;

import com.vetos.modules.integration.tarbil.domain.exception.TarbilServerRetryUnsupportedConflictException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarbilAdminAdapterTest {

    @Test
    void should_refuseServerSideRetry_becauseTheExtensionSubmitsWithTheVet() {
        assertThatThrownBy(() -> new TarbilAdminAdapter().retryNow(UUID.randomUUID()))
            .isInstanceOf(TarbilServerRetryUnsupportedConflictException.class)
            .hasMessageContaining("eklenti");
    }
}
