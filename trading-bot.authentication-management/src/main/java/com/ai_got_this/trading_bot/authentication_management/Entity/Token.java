package com.ai_got_this.trading_bot.authentication_management.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Entity
@Data
@Builder
public class Token {
    @Id
    private Long tokenId;
    private Integer userId;
    private Date createdAt;
    private Date expiredAt;
    private Boolean revokedStatus;
    private Long previousTokenId;
}
