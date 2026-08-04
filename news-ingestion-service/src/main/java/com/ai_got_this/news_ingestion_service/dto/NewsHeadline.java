package com.ai_got_this.news_ingestion_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NewsHeadline {
    private final String ticket;
    private final String headline;
}
