package com.vetos.modules.boarding.infrastructure.persistence;

import com.vetos.modules.boarding.domain.BoardingStay;
import com.vetos.modules.boarding.domain.BoardingStayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class BoardingStayRepositoryAdapter implements BoardingStayRepository {

    private final BoardingStayJpaRepository jpaRepository;

    @Override
    public BoardingStay save(BoardingStay stay) { return jpaRepository.save(stay); }

    @Override
    public Optional<BoardingStay> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<BoardingStay> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public List<BoardingStay> findByRoomId(UUID roomId) { return jpaRepository.findByRoomId(roomId); }
}
