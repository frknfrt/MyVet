package com.vetos.modules.boarding.infrastructure.persistence;

import com.vetos.modules.boarding.domain.BoardingRoom;
import com.vetos.modules.boarding.domain.BoardingRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class BoardingRoomRepositoryAdapter implements BoardingRoomRepository {

    private final BoardingRoomJpaRepository jpaRepository;

    @Override
    public BoardingRoom save(BoardingRoom room) { return jpaRepository.save(room); }

    @Override
    public Optional<BoardingRoom> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<BoardingRoom> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }
}
