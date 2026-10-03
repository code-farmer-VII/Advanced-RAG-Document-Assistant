package com.safaricom.hr.controller;

import com.safaricom.hr.dto.ChatRequest;
import com.safaricom.hr.dto.ChatResponse;
import com.safaricom.hr.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagService ragService;

    @PostMapping
    public ResponseEntity<ChatResponse> askQuestion(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = ragService.askQuestion(request);
        return ResponseEntity.ok(response);
    }
}
