package com.ai_got_this.accounts_service.service;

import com.ai_got_this.accounts_service.entity.User;
import com.ai_got_this.accounts_service.repo.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserService {
    UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void createUser(String userId, String email) {
        if (userRepository.existsById(userId)) {
            log.error("Attempt to create user with existing userId: {}", userId);
            return;
        }

        var user = User.builder()
                .userId(userId)
                .email(email)
                .createdAt(java.time.OffsetDateTime.now())
                .build();

        userRepository.save(user);
    }
}