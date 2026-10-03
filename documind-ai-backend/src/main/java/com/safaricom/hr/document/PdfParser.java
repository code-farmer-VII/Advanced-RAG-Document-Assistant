package com.safaricom.hr.document;

import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PdfParser {
    /**
     * Parses a PDF file and returns a list of Spring AI Documents.
     * Each returned Document should ideally represent a single page
     * and contain metadata like the page number and document name.
     */
    List<Document> parse(MultipartFile file, String documentId, String originalFilename);
}
