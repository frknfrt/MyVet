package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.Coupon;
import com.vetos.modules.platformadmin.domain.CouponDiscountType;
import com.vetos.modules.platformadmin.domain.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListCouponsUseCaseTest {

    @Mock private CouponRepository couponRepository;

    private ListCouponsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListCouponsUseCase(couponRepository);
    }

    @Test
    void should_returnAllCoupons_inRepositoryOrder() {
        Coupon c1 = Coupon.create("WELCOME10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null);
        Coupon c2 = Coupon.create("INDIRIM250", CouponDiscountType.FIXED_AMOUNT, new BigDecimal("250"), 10, null);
        when(couponRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(c2, c1));

        List<Coupon> result = useCase.execute();

        assertThat(result).containsExactly(c2, c1);
    }
}
