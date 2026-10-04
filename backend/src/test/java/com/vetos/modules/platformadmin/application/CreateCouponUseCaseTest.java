package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.CreateCouponCommand;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateCouponUseCaseTest {

    @Mock private CouponRepository couponRepository;

    private CreateCouponUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateCouponUseCase(couponRepository);
    }

    @Test
    void should_createCoupon_when_codeIsFree() {
        when(couponRepository.findByCode("WELCOME10")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        Coupon result = useCase.execute(new CreateCouponCommand("welcome10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), 50, null));

        assertThat(result.getCode()).isEqualTo("WELCOME10");
        assertThat(result.getMaxRedemptions()).isEqualTo(50);
        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        org.mockito.Mockito.verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("WELCOME10");
    }

    @Test
    void should_throwConflict_when_codeAlreadyExists_caseInsensitive() {
        Coupon existing = Coupon.create("WELCOME10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null);
        when(couponRepository.findByCode("WELCOME10")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(new CreateCouponCommand("Welcome10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null)))
            .isInstanceOf(CouponCodeAlreadyExistsConflictException.class);

        verifyNoMoreInteractions(couponRepository);
    }
}
