package com.ai_got_this.market_data_service.dto;

import lombok.Data;

@Data
public class AlpacaErrorMessage extends AlpacaMessage {
    private String msg;
    private Integer code;
}
