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
public class SemanticRetriever implements Retriever {

    private final DocumentVectorStore vectorStore;

    @Override
    public List<RetrievedChunkDTO> retrieve(String query, int topK, Map<String, Object> filters) {
        log.info("Executing Semantic Retrieval for query: {}", query);
        // We will pass topK to vector store
        // In a real implementation, we would also pass the metadata filters down to Qdrant.
        // For now, we perform the semantic search:
        List<RetrievedChunkDTO> results = vectorStore.search(query, topK);
        
        // Filter locally if Qdrant filter isn't passed down (for simplicity)
        if (filters != null && !filters.isEmpty()) {
            results = results.stream().filter(chunk -> {
                for (Map.Entry<String, Object> entry : filters.entrySet()) {
                    if (!entry.getValue().equals(chunk.getMetadata().get(entry.getKey()))) {
                        return false;
                    }
                }
                return true;
            }).toList();
        }

        return results;
    }
}
