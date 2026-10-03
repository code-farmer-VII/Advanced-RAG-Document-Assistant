package com.safaricom.hr.qdrant;

import com.safaricom.hr.dto.RetrievedChunkDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpringAiQdrantVectorStore implements DocumentVectorStore {

    private final VectorStore vectorStore;

    @Override
    public void store(List<Document> chunks) {
        log.info("Storing {} chunks into Qdrant...", chunks.size());
        // Spring AI VectorStore automatically generates embeddings for these documents
        // before saving them to Qdrant.
        vectorStore.add(chunks);
        log.info("Successfully stored chunks in Qdrant.");
    }

    @Override
    public List<RetrievedChunkDTO> search(String query, int topK) {
        log.info("Performing semantic search in Qdrant for query: {}", query);
        
        SearchRequest request = SearchRequest.defaults()
                .withQuery(query)
                .withTopK(topK);

        List<Document> results = vectorStore.similaritySearch(request);

        return results.stream().map(doc -> RetrievedChunkDTO.builder()
                .id(doc.getId())
                .content(doc.getContent())
                // Score is typically populated in metadata by Spring AI depending on the store
                .score(doc.getMetadata().containsKey("distance") ? 
                        (Double) doc.getMetadata().get("distance") : 0.0)
                .metadata(doc.getMetadata())
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    public void deleteByDocumentId(UUID documentId) {
        log.info("Deleting vectors for documentId: {}", documentId);
        // Note: As of Spring AI 1.0.0, deleting by filter might require custom Qdrant client usage 
        // or finding documents first then deleting by ID.
        // We will fetch the documents by metadata filter, then delete by their vector IDs.
        
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest request = SearchRequest.defaults()
                .withFilterExpression(b.eq("documentId", documentId.toString()).build())
                .withTopK(10000); // Fetch all chunks for this doc

        List<Document> chunksToDelete = vectorStore.similaritySearch(request);
        
        if (!chunksToDelete.isEmpty()) {
            List<String> ids = chunksToDelete.stream()
                    .map(Document::getId)
                    .collect(Collectors.toList());
            vectorStore.delete(ids);
            log.info("Deleted {} chunks from Qdrant.", ids.size());
        } else {
            log.info("No chunks found in Qdrant for documentId: {}", documentId);
        }
    }
}
