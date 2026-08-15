package com.ai_got_this.account_service.repository;

import com.ai_got_this.account_service.domain.AlpacaConnection;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlpacaConnectionRepository extends JpaRepository<AlpacaConnection, UUID> {
    Optional<AlpacaConnection> findByConnectionIdAndUserId(UUID id, UUID userId);

    List<AlpacaConnection> findByUserId(UUID userId);

    List<AlpacaConnection> findByStatus(AlpacaConnection.ConnectionStatus status);

    Optional<AlpacaConnection> findByUserIdAndEnvironmentAndAlpacaAccountId(
            UUID userId, AlpacaConnection.Environment environment, UUID alpacaAccountId);

    Optional<AlpacaConnection> findByEnvironmentAndAlpacaAccountId(
            AlpacaConnection.Environment e, UUID a);
}
