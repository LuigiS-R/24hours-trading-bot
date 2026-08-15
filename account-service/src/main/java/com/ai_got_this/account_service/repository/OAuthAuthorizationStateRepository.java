package com.ai_got_this.account_service.repository;

import com.ai_got_this.account_service.domain.OAuthAuthorizationState;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAuthorizationStateRepository
        extends JpaRepository<OAuthAuthorizationState, UUID> {
    Optional<OAuthAuthorizationState> findByStateHash(String hash);
}
