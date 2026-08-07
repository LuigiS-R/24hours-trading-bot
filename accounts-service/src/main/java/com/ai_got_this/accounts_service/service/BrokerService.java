package com.ai_got_this.accounts_service.service;

import com.ai_got_this.accounts_service.dto.GetAccountResponse;

public interface BrokerService {
    GetAccountResponse getAccount(String keyId, String keySecret);
}
