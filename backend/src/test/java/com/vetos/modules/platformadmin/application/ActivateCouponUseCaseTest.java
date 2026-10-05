package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.Coupon;
import com.vetos.modules.platformadmin.domain.CouponDiscountType;
import com.vetos.modules.platformadmin.domain.CouponRepository;
import com.vetos.modules.platformadmin.domain.exception.CouponNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivateCouponUseCaseTest {

    @Mock private CouponRepository couponRepository;
    @Mock private RecordAuditLogUseCase recordAuditLogUseCase;

    private ActivateCouponUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ActivateCouponUseCase(couponRepository, recordAuditLogUseCase);
    }

    @Test
    void should_activateCoupon_when_found() {
        Coupon coupon = Coupon.create("WELCOME10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null);
        coupon.deactivate();
        UUID id = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ReflectionTestUtils.setField(coupon, "id", id);
        when(couponRepository.findById(id)).thenReturn(Optional.of(coupon));

        useCase.execute(id, adminId, "admin@vetly.com.tr");

        assertThat(coupon.isActive()).isTrue();
        verify(couponRepository).save(coupon);
        verify(recordAuditLogUseCase).execute(eq(adminId), eq("admin@vetly.com.tr"), eq(AuditAction.COUPON_ACTIVATED), eq("COUPON"), eq(id), any());
    }

    @Test
    void should_throwNotFound_when_couponDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(couponRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, UUID.randomUUID(), "admin@vetly.com.tr")).isInstanceOf(CouponNotFoundException.class);
        verifyNoInteractions(recordAuditLogUseCase);
    }
}
