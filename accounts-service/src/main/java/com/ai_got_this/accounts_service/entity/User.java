package com.ai_got_this.accounts_service.entity;

import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class User {
    @Id
    String userId;
    String email;
    OffsetDateTime createdAt;

}
