package com.safaricom.hr.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DocumentResponse {
    private UUID id;
    private String filename;
    private String originalFilename;
    private Long fileSize;
    private String status;
    private LocalDateTime createdAt;
}
