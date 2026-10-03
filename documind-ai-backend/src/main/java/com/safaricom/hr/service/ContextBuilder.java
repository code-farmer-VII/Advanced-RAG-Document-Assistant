package com.safaricom.hr.service;

import com.safaricom.hr.dto.RetrievedChunkDTO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContextBuilder {

    /**
     * Formats the retrieved and reranked chunks into a structured text format
     * to be injected into the final LLM Prompt.
     */
    public String buildContext(List<RetrievedChunkDTO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "No relevant context found.";
        }

        StringBuilder contextBuilder = new StringBuilder();

        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunkDTO chunk = chunks.get(i);
            
            String documentName = (String) chunk.getMetadata().getOrDefault("documentName", "Unknown Document");
            Object pageObj = chunk.getMetadata().get("page_number");
            String pageNumber = (pageObj != null) ? pageObj.toString() : "Unknown";

            contextBuilder.append(String.format("--- SOURCE %d ---\n", i + 1));
            contextBuilder.append(String.format("Document: %s\n", documentName));
            contextBuilder.append(String.format("Page: %s\n", pageNumber));
            
            // Add section if available
            if (chunk.getMetadata().containsKey("section")) {
                contextBuilder.append(String.format("Section: %s\n", chunk.getMetadata().get("section")));
            }
            
            contextBuilder.append("\n");
            contextBuilder.append(chunk.getContent().trim());
            contextBuilder.append("\n\n");
        }

        return contextBuilder.toString().trim();
    }
}
