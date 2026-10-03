package com.safaricom.hr.document;

import com.safaricom.hr.exception.DocumentProcessingException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Component
public class SpringAiPdfParser implements PdfParser {

    @Override
    public List<Document> parse(MultipartFile file, String documentId, String originalFilename) {
        Path tempFile = null;
        try {
            // Save the uploaded file to a temporary file
            tempFile = Files.createTempFile("upload-", ".pdf");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            Resource resource = new UrlResource(tempFile.toUri());

            // Configure the reader to parse page by page
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageExtractedTextFormatter(org.springframework.ai.reader.ExtractedTextFormatter.builder()
                            .withNumberOfBottomTextLinesToDelete(0)
                            .withNumberOfTopTextLinesToDelete(0)
                            .build())
                    .withPagesPerDocument(1) // One document per page
                    .build();

            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);
            List<Document> documents = pdfReader.get();

            // Enrich metadata with document ID and original filename
            for (Document doc : documents) {
                doc.getMetadata().put("documentId", documentId);
                doc.getMetadata().put("documentName", originalFilename);
                // PagePdfDocumentReader automatically populates the "page_number" metadata key (varies slightly by version)
            }

            return documents;

        } catch (Exception e) {
            throw new DocumentProcessingException("Failed to parse PDF file: " + originalFilename, e);
        } finally {
            // Clean up the temporary file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
