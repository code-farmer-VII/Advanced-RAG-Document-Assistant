package com.safaricom.hr.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CitationDTO {
    private String documentId;
    private String documentName;
    private Integer pageNumber;
    private String chunkId;
}
