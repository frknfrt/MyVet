package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.CreateCouponCommand;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.Coupon;
import com.vetos.modules.platformadmin.domain.CouponDiscountType;
import com.vetos.modules.platformadmin.domain.CouponRepository;
import com.vetos.modules.platformadmin.domain.exception.CouponCodeAlreadyExistsConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateCouponUseCaseTest {

    @Mock private CouponRepository couponRepository;
    @Mock private RecordAuditLogUseCase recordAuditLogUseCase;

    private CreateCouponUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateCouponUseCase(couponRepository, recordAuditLogUseCase);
    }

    @Test
    void should_createCoupon_when_codeIsFree() {
        UUID adminId = UUID.randomUUID();
        when(couponRepository.findByCode("WELCOME10")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        Coupon result = useCase.execute(
            new CreateCouponCommand("welcome10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), 50, null),
            adminId, "admin@vetly.com.tr"
        );

        assertThat(result.getCode()).isEqualTo("WELCOME10");
        assertThat(result.getMaxRedemptions()).isEqualTo(50);
        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("WELCOME10");
        verify(recordAuditLogUseCase).execute(eq(adminId), eq("admin@vetly.com.tr"), eq(AuditAction.COUPON_CREATED), eq("COUPON"), any(), any());
    }

    @Test
    void should_throwConflict_when_codeAlreadyExists_caseInsensitive() {
        Coupon existing = Coupon.create("WELCOME10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null);
        when(couponRepository.findByCode("WELCOME10")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(
            new CreateCouponCommand("Welcome10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null),
            UUID.randomUUID(), "admin@vetly.com.tr"
        )).isInstanceOf(CouponCodeAlreadyExistsConflictException.class);

        verifyNoMoreInteractions(couponRepository);
    }
}
