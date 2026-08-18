package com.ai_got_this.account_service.repository;

import com.ai_got_this.account_service.domain.PositionProjection;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionProjectionRepository extends JpaRepository<PositionProjection, UUID> {
    List<PositionProjection> findByConnectionId(UUID id);
}
