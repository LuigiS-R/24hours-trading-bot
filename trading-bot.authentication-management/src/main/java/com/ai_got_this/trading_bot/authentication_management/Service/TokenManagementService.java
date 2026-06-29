package com.ai_got_this.trading_bot.authentication_management.Service;

import com.ai_got_this.trading_bot.authentication_management.Config.ApplicationProperties;
import com.ai_got_this.trading_bot.authentication_management.Entity.Token;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class TokenManagementService {
    private ApplicationProperties applicationProperties;

    public TokenManagementService(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    public String generateJWT(String username, int userId) {
        String jwtSecret = applicationProperties.getJwtSecret();
        long jwtExpiration = applicationProperties.getJwtExpiration();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        String accessToken = generateAccessJWT(username, now, expiryDate, jwtSecret);
        generateRefreshJWT(userId, now, expiryDate);
        return accessToken;
    }

    private String generateAccessJWT(String username, Date now, Date expiryDate, String jwtSecret) {
        return Jwts.builder()
                .setSubject(username)                           // who is this token for?
                .setIssuedAt(now)                               // when was it created?
                .setExpiration(expiryDate)                      // when does it expire?
                .signWith(SignatureAlgorithm.HS512, jwtSecret)  // sign it with secret
                .compact();
        
    }

    private void generateRefreshJWT(int userId, Date now, Date expiryDate) {
        Long previousTokenId = null; // This should be set to the ID of the previous token if applicable

        Token refreshToken = Token.builder()
                .userId(userId)
                .createdAt(now)
                .expiredAt(expiryDate)
                .revokedStatus(false)
                .previousTokenId(previousTokenId)
                .build();
    }
}
