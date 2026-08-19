package com.ai_got_this.order_execution_service.repository;

import com.ai_got_this.order_execution_service.domain.OrderProjection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderProjectionRepository extends JpaRepository<OrderProjection, UUID> {
    Optional<OrderProjection> findByAlpacaOrderId(UUID alpacaOrderId);

    List<OrderProjection> findTop20ByConnectionIdOrderByUpdatedAtDesc(UUID connectionId);
}
