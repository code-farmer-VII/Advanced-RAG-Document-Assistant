package com.safaricom.hr.service;

import com.safaricom.hr.document.PdfParser;
import com.safaricom.hr.dto.DocumentResponse;
import com.safaricom.hr.entity.DocumentEntity;
import com.safaricom.hr.exception.DocumentProcessingException;
import com.safaricom.hr.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final PdfParser pdfParser;
    private final com.safaricom.hr.document.TextChunker textChunker;
    private final com.safaricom.hr.qdrant.DocumentVectorStore vectorStore;

    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file) {
        if (file.isEmpty() || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            throw new DocumentProcessingException("Only non-empty PDF files are supported.");
        }

        // 1. Save document metadata to DB initially as PROCESSING
        DocumentEntity entity = DocumentEntity.builder()
                .originalFilename(file.getOriginalFilename())
                .filename(UUID.randomUUID().toString() + ".pdf")
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .status("PROCESSING")
                .build();
        
        entity = documentRepository.save(entity);

        try {
            // 2. Parse PDF and extract pages
            log.info("Parsing document: {}", entity.getOriginalFilename());
            List<Document> parsedPages = pdfParser.parse(file, entity.getId().toString(), entity.getOriginalFilename());
            
            // 3. Chunking & Embedding
            log.info("Chunking document: {}", entity.getOriginalFilename());
            List<Document> chunks = textChunker.chunk(parsedPages);
            log.info("Created {} chunks", chunks.size());
            vectorStore.store(chunks);

            // 4. Update status to COMPLETED
            entity.setStatus("COMPLETED");
            entity = documentRepository.save(entity);

            log.info("Successfully processed document: {}", entity.getOriginalFilename());
            return mapToResponse(entity);

        } catch (Exception e) {
            log.error("Failed to process document", e);
            entity.setStatus("FAILED");
            documentRepository.save(entity);
            throw new DocumentProcessingException("Failed to process document", e);
        }
    }

    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void deleteDocument(UUID id) {
        DocumentEntity entity = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentProcessingException("Document not found"));
        
        // Delete vectors from Qdrant
        vectorStore.deleteByDocumentId(id);

        // Delete metadata from DB
        documentRepository.delete(entity);
    }

    private DocumentResponse mapToResponse(DocumentEntity entity) {
        return DocumentResponse.builder()
                .id(entity.getId())
                .filename(entity.getFilename())
                .originalFilename(entity.getOriginalFilename())
                .fileSize(entity.getFileSize())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
