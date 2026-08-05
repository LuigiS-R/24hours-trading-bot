package com.ai_got_this.market_data_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarketData {
    private String ticket;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Long volume;
    private Double returnPercentage;
    private Double movingAverage5;
    private Double movingAverage20;
    private Double volatility5;
    private Double rsi14;
    private Double macd;
}
