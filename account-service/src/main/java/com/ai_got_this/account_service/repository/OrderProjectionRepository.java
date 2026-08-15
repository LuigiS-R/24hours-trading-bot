package com.ai_got_this.account_service.repository;

import com.ai_got_this.account_service.domain.OrderProjection;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderProjectionRepository extends JpaRepository<OrderProjection, UUID> {
    List<OrderProjection> findTop20ByConnectionIdOrderByUpdatedAtDesc(UUID id);

    Optional<OrderProjection> findByAlpacaOrderId(UUID id);
}
