package com.vetos.modules.platformadmin.application;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeactivateCouponUseCaseTest {

    @Mock private CouponRepository couponRepository;

    private DeactivateCouponUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeactivateCouponUseCase(couponRepository);
    }

    @Test
    void should_deactivateCoupon_when_found() {
        Coupon coupon = Coupon.create("WELCOME10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(coupon, "id", id);
        when(couponRepository.findById(id)).thenReturn(Optional.of(coupon));

        useCase.execute(id);

        assertThat(coupon.isActive()).isFalse();
        verify(couponRepository).save(coupon);
    }

    @Test
    void should_throwNotFound_when_couponDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(couponRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(CouponNotFoundException.class);
    }
}
