package com.safaricom.hr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class ChatRequest {
    private UUID conversationId;
    
    @NotBlank(message = "Question cannot be empty")
    private String question;
}
