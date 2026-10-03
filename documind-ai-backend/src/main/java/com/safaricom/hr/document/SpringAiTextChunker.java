package com.safaricom.hr.document;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpringAiTextChunker implements TextChunker {

    // Target around 500-800 tokens.
    // The TokenTextSplitter defaults to around 800 chunk size with 350 overlap,
    // but we can configure it explicitly.
    private final TokenTextSplitter splitter;

    public SpringAiTextChunker() {
        // chunk size 800, overlap 100
        this.splitter = new TokenTextSplitter(800, 100, 5, 10000, true);
    }

    @Override
    public List<Document> chunk(List<Document> documents) {
        List<Document> chunks = splitter.split(documents);
        
        // Enrich chunks with index and deterministic content hash
        int chunkIndex = 0;
        for (Document chunk : chunks) {
            chunk.getMetadata().put("chunkIndex", chunkIndex++);
            chunk.getMetadata().put("contentHash", generateHash(chunk.getContent()));
            // We ensure we carry over page_number, documentId, and documentName
        }

        return chunks;
    }

    private String generateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
