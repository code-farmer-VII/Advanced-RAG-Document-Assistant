package com.safaricom.hr.service;

import com.safaricom.hr.entity.MessageEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QueryRewriteService {

    private final ChatClient chatClient;

    public QueryRewriteService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String rewriteQuery(String currentQuestion, List<MessageEntity> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return currentQuestion;
        }

        log.info("Rewriting query based on conversation history...");

        String historyText = conversationHistory.stream()
                .map(msg -> msg.getRole() + ": " + msg.getContent())
                .collect(Collectors.joining("\n"));

        String prompt = String.format("""
                You are a search query rewriting assistant.
                Given the following conversation history and the user's latest question,
                rewrite the latest question into a standalone, search-optimized query.
                
                Resolve any pronouns (like "it", "they", "this") to their actual subjects from the history.
                Do not answer the question. Only output the rewritten search query.
                If the question is already clear and standalone, output it as is.
                
                Conversation History:
                %s
                
                Latest Question: %s
                
                Rewritten Query:""", historyText, currentQuestion);

        try {
            String rewrittenQuery = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (StringUtils.hasText(rewrittenQuery)) {
                rewrittenQuery = rewrittenQuery.replace("\"", "").trim();
                log.info("Original: '{}' -> Rewritten: '{}'", currentQuestion, rewrittenQuery);
                return rewrittenQuery;
            }
        } catch (Exception e) {
            log.warn("Failed to rewrite query, falling back to original question. Error: {}", e.getMessage());
        }

        return currentQuestion;
    }
}
