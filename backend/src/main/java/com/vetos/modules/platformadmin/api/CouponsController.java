package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.CouponResponse;
import com.vetos.modules.platformadmin.api.dto.CreateCouponRequest;
import com.vetos.modules.platformadmin.application.ActivateCouponUseCase;
import com.vetos.modules.platformadmin.application.CreateCouponUseCase;
import com.vetos.modules.platformadmin.application.DeactivateCouponUseCase;
import com.vetos.modules.platformadmin.application.ListCouponsUseCase;
import com.vetos.modules.platformadmin.application.dto.CreateCouponCommand;
import com.vetos.modules.platformadmin.domain.Coupon;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Platform admin "Kuponlar" paneli -- vetly.com kayit akisindaki ilk odemeye
 * indirim uygulayan kodlarin olusturulmasi/listelenmesi/aktif-pasif edilmesi.
 */
@RestController
@RequestMapping("/api/v1/platform-admin/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class CouponsController {

    private final ListCouponsUseCase listCouponsUseCase;
    private final CreateCouponUseCase createCouponUseCase;
    private final ActivateCouponUseCase activateCouponUseCase;
    private final DeactivateCouponUseCase deactivateCouponUseCase;

    @GetMapping
    public List<CouponResponse> list() {
        return listCouponsUseCase.execute().stream().map(CouponResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<CouponResponse> create(@RequestBody @Valid CreateCouponRequest request) {
        Coupon coupon = createCouponUseCase.execute(new CreateCouponCommand(
            request.code(), request.discountType(), request.discountValue(), request.maxRedemptions(), request.expiresAt()
        ));
        return ResponseEntity.status(201).body(CouponResponse.from(coupon));
    }

    @PostMapping("/{id}/activate")
    public void activate(@PathVariable UUID id) {
        activateCouponUseCase.execute(id);
    }

    @PostMapping("/{id}/deactivate")
    public void deactivate(@PathVariable UUID id) {
        deactivateCouponUseCase.execute(id);
    }
}
