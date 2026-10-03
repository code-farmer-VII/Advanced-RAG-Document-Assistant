package com.safaricom.hr.retrieval;

import com.safaricom.hr.dto.RetrievedChunkDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class FallbackReranker implements Reranker {

    // Note: If we had a dedicated API like Cohere Rerank, we would implement a CohereReranker.
    // To keep it fast and prevent LLM API rate limits on this portfolio app, 
    // we use a simple pass-through reranker that relies on the already robust RRF scores 
    // from our Hybrid search, returning the exact topK requested.
    
    @Override
    public List<RetrievedChunkDTO> rerank(String query, List<RetrievedChunkDTO> candidates, int topK) {
        log.info("Reranking {} candidates for top {} results...", candidates.size(), topK);
        
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        // The candidates are already sorted by RRF from the HybridRetriever.
        // If we want a strict LLM-based verification, we could prompt the LLM here to score true/false.
        // For the fallback, we simply slice the topK.
        
        List<RetrievedChunkDTO> reranked = candidates.stream()
                .limit(topK)
                .collect(Collectors.toList());
                
        log.info("Reranking complete. Selected {} chunks.", reranked.size());
        return reranked;
    }
}
