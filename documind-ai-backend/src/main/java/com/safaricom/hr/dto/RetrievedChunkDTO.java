package com.safaricom.hr.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class RetrievedChunkDTO {
    private String id;
    private String content;
    private double score;
    private Map<String, Object> metadata;
}
