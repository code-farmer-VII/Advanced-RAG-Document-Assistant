package com.safaricom.hr.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ChatResponse {
    private String answer;
    private List<CitationDTO> citations;
    private Map<String, Object> metadata;
}
