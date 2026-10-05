package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.Coupon;
import com.vetos.modules.platformadmin.domain.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class CouponRepositoryAdapter implements CouponRepository {

    private final CouponJpaRepository jpaRepository;

    @Override
    public Coupon save(Coupon coupon) { return jpaRepository.save(coupon); }

    @Override
    public Optional<Coupon> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<Coupon> findByCode(String code) { return jpaRepository.findByCode(code); }

    @Override
    public List<Coupon> findAllByOrderByCreatedAtDesc() { return jpaRepository.findAllByOrderByCreatedAtDesc(); }
}
