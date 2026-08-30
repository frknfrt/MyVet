package com.vetos.modules.platformadmin.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantSignupRequestTest {

    @Test
    void should_startAsPending_when_created() {
        TenantSignupRequest request = TenantSignupRequest.create(
            "Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", "+905551112233", "PRO"
        );

        assertThat(request.getClinicName()).isEqualTo("Mutlu Pati");
        assertThat(request.getAdminFullName()).isEqualTo("Ayse Yilmaz");
        assertThat(request.getAdminEmail()).isEqualTo("ayse@example.com");
        assertThat(request.getPhone()).isEqualTo("+905551112233");
        assertThat(request.getPlanCode()).isEqualTo("PRO");
        assertThat(request.isCompleted()).isFalse();
    }

    @Test
    void should_allowNullPhone_when_created() {
        TenantSignupRequest request = TenantSignupRequest.create(
            "Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO"
        );

        assertThat(request.getPhone()).isNull();
    }

    @Test
    void should_becomeCompleted_when_completeCalled() {
        TenantSignupRequest request = TenantSignupRequest.create(
            "Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO"
        );

        request.complete();

        assertThat(request.isCompleted()).isTrue();
    }

    @Test
    void should_stayCompleted_when_completeCalledTwice() {
        TenantSignupRequest request = TenantSignupRequest.create(
            "Mutlu Pati", "Ayse Yilmaz", "ayse@example.com", null, "PRO"
        );
        request.complete();

        request.complete();

        assertThat(request.isCompleted()).isTrue();
    }
}
