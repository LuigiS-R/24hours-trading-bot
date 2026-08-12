package com.ai_got_this.accounts_service.entity;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    String userId;
    String email;
    OffsetDateTime createdAt;

}
