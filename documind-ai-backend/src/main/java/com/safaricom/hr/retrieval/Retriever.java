package com.safaricom.hr.retrieval;

import com.safaricom.hr.dto.RetrievedChunkDTO;
import java.util.List;
import java.util.Map;

public interface Retriever {
    List<RetrievedChunkDTO> retrieve(String query, int topK, Map<String, Object> filters);
}
