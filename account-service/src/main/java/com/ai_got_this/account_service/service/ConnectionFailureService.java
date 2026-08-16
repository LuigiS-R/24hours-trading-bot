package com.ai_got_this.account_service.service;

import com.ai_got_this.account_service.domain.AlpacaConnection;
import com.ai_got_this.account_service.repository.AlpacaConnectionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionFailureService {

    private final AlpacaConnectionRepository alpacaConnectionRepository;

    public ConnectionFailureService(AlpacaConnectionRepository alpacaConnectionRepository) {
        this.alpacaConnectionRepository = alpacaConnectionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markConnectionFailure(UUID connectionId, String errorCode) {
        alpacaConnectionRepository.findById(connectionId).ifPresent(connection -> {
            connection.setLastErrorCode(errorCode);
            if ("ALPACA_REAUTH_REQUIRED".equals(errorCode)) {
                connection.setStatus(AlpacaConnection.ConnectionStatus.REAUTH_REQUIRED);
            } else {
                connection.setStatus(AlpacaConnection.ConnectionStatus.ERROR);
            }
        });
    }
}
