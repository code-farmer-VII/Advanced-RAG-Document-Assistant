package com.safaricom.hr.document;

import org.springframework.ai.document.Document;

import java.util.List;

public interface TextChunker {
    /**
     * Splits a list of documents (usually pages) into smaller chunks
     * suitable for vector embeddings.
     */
    List<Document> chunk(List<Document> documents);
}
