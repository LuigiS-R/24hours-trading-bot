package com.ai_got_this.news_ingestion_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AlpacaMessage {
    @JsonProperty("T")
    private String type;
    private String id;
    private String headline;
    private List<String> symbols;
    private String msg;
    private Integer code;
}
