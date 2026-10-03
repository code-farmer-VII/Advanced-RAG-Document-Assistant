package com.safaricom.hr.retrieval;

import com.safaricom.hr.dto.RetrievedChunkDTO;
import java.util.List;

public interface Reranker {
    
    /**
     * Reranks a list of candidate chunks based on their relevance to the query.
     */
    List<RetrievedChunkDTO> rerank(String query, List<RetrievedChunkDTO> candidates, int topK);
}
