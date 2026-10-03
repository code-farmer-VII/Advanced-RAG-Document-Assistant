package com.safaricom.hr.retrieval;

import com.safaricom.hr.dto.RetrievedChunkDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class HybridRetriever implements Retriever {

    private final SemanticRetriever semanticRetriever;
    private final KeywordRetriever keywordRetriever;
    private final ResultFusion resultFusion;

    @Override
    public List<RetrievedChunkDTO> retrieve(String query, int topK, Map<String, Object> filters) {
        log.info("Executing Hybrid Retrieval for query: {}", query);
        
        // 1. Retrieve candidates independently
        // We retrieve more than topK to have a good fusion pool
        int candidatePoolSize = topK * 3;
        List<RetrievedChunkDTO> semanticResults = semanticRetriever.retrieve(query, candidatePoolSize, filters);
        List<RetrievedChunkDTO> keywordResults = keywordRetriever.retrieve(query, candidatePoolSize, filters);

        log.info("Semantic Results: {}, Keyword Results: {}", semanticResults.size(), keywordResults.size());

        // 2. Fuse candidates using RRF
        List<RetrievedChunkDTO> fusedResults = resultFusion.fuse(
                Arrays.asList(semanticResults, keywordResults), 
                topK
        );

        log.info("Fused Results: {}", fusedResults.size());
        
        return fusedResults;
    }
}
