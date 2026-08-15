package com.ai_got_this.account_service.repository;

import com.ai_got_this.account_service.domain.OutboxEvent;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAt(String status);
}
