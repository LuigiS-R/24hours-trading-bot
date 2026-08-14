package com.ai_got_this.account_service.domain;

import lombok.Data;

import jakarta.persistence.*;
import java.util.UUID;



@Data
@Entity
@Table(name="app_user")
public class User extends AuditedEntity {
    @Id
    private UUID userId;

    @Column(nullable=false, unique=true)
    private String externalSubject;

    private String email;
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private UserStatus status=UserStatus.ACTIVE;

    public enum UserStatus {
        ACTIVE, DISABLED
    }
}
