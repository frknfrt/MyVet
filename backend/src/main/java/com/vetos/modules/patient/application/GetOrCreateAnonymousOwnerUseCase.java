package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Hizli Satis akisinda kayitli olmayan/kayit gerekmeyen musteriler icin
 * kullanilir -- bkz. docs/superpowers/specs/2026-09-14-hizli-satis-design.md
 * S4.1. Nadir bir race durumunda (ayni anda iki ilk-satis) iki sentinel
 * owner olusabilir; veri butunlugunu bozmadigi icin ekstra kilitleme
 * eklenmedi (YAGNI).
 */
@Service
@RequiredArgsConstructor
public class GetOrCreateAnonymousOwnerUseCase {

    private final OwnerRepository ownerRepository;

    @Transactional
    public UUID execute(UUID tenantId) {
        return ownerRepository.findAnonymousPlaceholder(tenantId)
            .map(Owner::getId)
            .orElseGet(() -> ownerRepository.save(Owner.createAnonymousPlaceholder(tenantId)).getId());
    }
}
