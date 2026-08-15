package com.ai_got_this.account_service.repository;

import com.ai_got_this.account_service.domain.User;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByExternalSubject(String subject);
}
