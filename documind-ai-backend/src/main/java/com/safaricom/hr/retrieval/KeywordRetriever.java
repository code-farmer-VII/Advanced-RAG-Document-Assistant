package com.safaricom.hr.retrieval;

import com.safaricom.hr.dto.RetrievedChunkDTO;
import com.safaricom.hr.qdrant.DocumentVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class KeywordRetriever implements Retriever {

    private final DocumentVectorStore vectorStore;

    @Override
    public List<RetrievedChunkDTO> retrieve(String query, int topK, Map<String, Object> filters) {
        log.info("Executing Keyword Retrieval for query: {}", query);
        
        // Note: Standard Spring AI VectorStore does not yet support BM25 natively.
        // As a workaround for this architecture, we will simulate keyword search 
        // by fetching a larger candidate pool semantically and re-scoring based on term frequency.
        // In production, Qdrant's sparse vectors or a dedicated Elasticsearch cluster would be used.
        
        List<RetrievedChunkDTO> candidates = vectorStore.search(query, topK * 3);
        
        // Apply metadata filters
        if (filters != null && !filters.isEmpty()) {
            candidates = candidates.stream().filter(chunk -> {
                for (Map.Entry<String, Object> entry : filters.entrySet()) {
                    if (!entry.getValue().equals(chunk.getMetadata().get(entry.getKey()))) {
                        return false;
                    }
                }
                return true;
            }).toList();
        }

        String[] queryTerms = query.toLowerCase().split("\\W+");
        
        for (RetrievedChunkDTO candidate : candidates) {
            double keywordScore = 0.0;
            String content = candidate.getContent().toLowerCase();
            for (String term : queryTerms) {
                if (term.length() > 3 && content.contains(term)) {
                    // Simple term frequency scoring
                    int count = content.split(term, -1).length - 1;
                    keywordScore += count;
                }
            }
            // Normalize score arbitrarily for RRF compatibility
            candidate.setScore(keywordScore / 10.0);
        }

        // Sort by keyword score descending
        return candidates.stream()
                .filter(c -> c.getScore() > 0)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .toList();
    }
}
