package com.safaricom.hr.qdrant;

import com.safaricom.hr.dto.RetrievedChunkDTO;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

public interface DocumentVectorStore {
    
    /**
     * Generates embeddings and stores the document chunks in Qdrant.
     */
    void store(List<Document> chunks);

    /**
     * Performs a semantic similarity search.
     */
    List<RetrievedChunkDTO> search(String query, int topK);

    /**
     * Deletes all chunks associated with a specific document ID.
     */
    void deleteByDocumentId(UUID documentId);
}
