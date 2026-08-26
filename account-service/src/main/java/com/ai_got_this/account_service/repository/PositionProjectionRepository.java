package com.ai_got_this.account_service.repository;

import com.ai_got_this.account_service.domain.PositionProjection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionProjectionRepository extends JpaRepository<PositionProjection, UUID> {
    List<PositionProjection> findAllByOrderBySymbolAsc();
}
