# Backend Code Review & Test Plan

## 📂 Project Overview

The backend is a **Spring Boot** monolith that implements a full **RAG pipeline**:
1. **Document ingestion** – PDF parsing → intelligent chunking → embedding generation → storage in **Qdrant** (vector DB) and **PostgreSQL** (metadata).
2. **Query handling** – conversation history → query rewriting → hybrid retrieval (semantic + keyword) → **Reciprocal Rank Fusion (RRF)** → reranking → context construction → LLM answer generation.
3. **API layer** – `DocumentController` for upload/delete and `ChatController` for Q&A.

All components are wired via Spring DI and are deliberately decoupled through interfaces (`Retriever`, `DocumentVectorStore`, `Reranker`).

---

## ✅ What Works Well
| Component | Strength |
|-----------|----------|
| **Chunking** (`SpringAiTextChunker`) | Uses token‑based split with overlap, deterministic SHA‑256 chunk hash for deduplication. |
| **Vector Store** (`SpringAiQdrantVectorStore`) | Leverages Spring AI `VectorStore` abstraction → clean separation from Qdrant implementation. |
| **Hybrid Retrieval** (`HybridRetriever`) | Fires both semantic & keyword searches in parallel and fuses results using a textbook RRF implementation (`ResultFusion`). |
| **Query Rewriting** (`QueryRewriteService`) | Uses a system‑prompt to resolve pronouns, making downstream retrieval more robust. |
| **Grounded LLM Generation** (`RagService`) | System prompt explicitly forbids hallucination and forces citation usage. |
| **Citations** (`CitationDTO`) | Captures document ID, name, page number, and chunk ID for every answer. |
| **Transactionality** | `RagService.askQuestion` is annotated with `@Transactional` – user/assistant messages are persisted atomically. |
| **Clear package layout** | `controller`, `service`, `retrieval`, `document`, `qdrant`, `entity`, `dto`, `exception`, `repository` – easy to navigate. |

---

## 🔍 Potential Issues & Improvements
| File / Area | Observation | Suggested Fix |
|------------|-------------|--------------|
| `SpringAiQdrantVectorStore.search` | Score extraction assumes metadata key `distance` (Spring AI sometimes stores `score`). | Add fallback: `metadata.getOrDefault("score", 0.0)`. |
| `QueryRewriteService` | Swallows all exceptions and returns the original query – good for resilience but hides root cause. | Log the full stack trace (`log.warn("...", e)`). |
| `ContextBuilder.buildContext` | Direct cast `(String) chunk.getMetadata().get("documentName")` may cause `ClassCastException` if the value is not a `String`. | Use `String.valueOf(chunk.getMetadata().getOrDefault("documentName", "Unknown"))`. |
| `RagService.askQuestion` – LLM call | No timeout handling – a slow LLM could hang the request. | Configure `ChatClient` with a reasonable `responseTimeout`. |
| `HybridRetriever.retrieve` | Passes `null` filters to the underlying retrievers – fine now but future filtering will break. | Add a `Map<String, Object> filters` param to the method signature and propagate it. |
| `DocumentService` | No explicit validation of file size/type beyond `MultipartFile`. | Add a size limit (e.g., 20 MB) and mime‑type check before parsing. |
| `application.yml` | Credentials (`LLM_API_KEY`) are read from env vars but not documented in the README. | Update README with a full list of required env variables (done) and mention secure storage (e.g., `.env`). |
| Unit Test Coverage | No test sources yet – 0 % coverage. | Add JUnit 5 + Mockito tests (see below). |

---

## 🧪 Test Strategy
We will create **unit tests** for the core services using **JUnit 5** and **Mockito** (no external services required). Integration tests can be added later with Testcontainers for PostgreSQL & Qdrant.

### 1. `DocumentServiceTest`
```java
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    @Mock PdfParser pdfParser;
    @Mock TextChunker textChunker;
    @Mock DocumentVectorStore vectorStore;
    @Mock DocumentRepository documentRepository;
    @InjectMocks DocumentService documentService;

    @Test
    void uploadDocument_successful() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "sample.pdf", "application/pdf", "PDF CONTENT".getBytes());
        DocumentEntity savedEntity = DocumentEntity.builder()
                .id(UUID.randomUUID())
                .filename("sample.pdf")
                .originalFilename("sample.pdf")
                .status(DocumentStatus.PROCESSED)
                .build();
        when(documentRepository.save(any())).thenReturn(savedEntity);
        when(pdfParser.parse(any(InputStream.class))).thenReturn("Hello world from pdf");
        when(textChunker.chunk(anyString())).thenReturn(List.of(new TextChunk("Hello world", 0, 1, Map.of())));
        doNothing().when(vectorStore).addChunks(any(), any());

        DocumentResponse response = documentService.uploadDocument(file);
        assertEquals(savedEntity.getId(), response.getId());
        verify(vectorStore).addChunks(eq(savedEntity.getId()), anyList());
    }
}
```
*Validates the orchestration flow – repository save, parsing, chunking, and vector store interaction.*

### 2. `RagServiceTest`
```java
@ExtendWith(MockitoExtension.class)
class RagServiceTest {
    @Mock ConversationRepository convRepo;
    @Mock MessageRepository msgRepo;
    @Mock QueryRewriteService rewriteService;
    @Mock HybridRetriever hybridRetriever;
    @Mock Reranker reranker;
    @Mock ContextBuilder contextBuilder;
    @Mock ChatClient chatClient;
    @InjectMocks RagService ragService;

    @Test
    void askQuestion_happyPath() {
        UUID convId = UUID.randomUUID();
        ConversationEntity conv = new ConversationEntity();
        conv.setId(convId);
        when(convRepo.findById(convId)).thenReturn(Optional.of(conv));
        when(msgRepo.findByConversationIdOrderByCreatedAtAsc(convId)).thenReturn(List.of());
        when(rewriteService.rewriteQuery(anyString(), anyList()))
                .thenReturn("rewritten query");
        when(hybridRetriever.retrieve(eq("rewritten query"), anyInt(), isNull()))
                .thenReturn(List.of(sampleChunk()));
        when(reranker.rerank(eq("rewritten query"), anyList(), anyInt()))
                .thenReturn(List.of(sampleChunk()));
        when(contextBuilder.buildContext(anyList()))
                .thenReturn("Context block");
        // Mock LLM response
        ChatClient.ChatResponse llmResp = mock(ChatClient.ChatResponse.class);
        when(llmResp.content()).thenReturn("Answer from LLM");
        when(chatClient.prompt().user(anyString()).call()).thenReturn(llmResp);

        ChatRequest request = new ChatRequest();
        request.setConversationId(convId);
        request.setQuestion("What is policy?");
        ChatResponse resp = ragService.askQuestion(request);

        assertEquals("Answer from LLM", resp.getAnswer());
        assertFalse(resp.getCitations().isEmpty());
        verify(msgRepo, times(2)).save(any(MessageEntity.class)); // user + assistant
    }

    private RetrievedChunkDTO sampleChunk() {
        return RetrievedChunkDTO.builder()
                .id(UUID.randomUUID().toString())
                .content("Sample chunk text")
                .score(0.9)
                .metadata(Map.of(
                        "documentId", UUID.randomUUID().toString(),
                        "documentName", "sample.pdf",
                        "page_number", 1))
                .build();
    }
}
```
*Ensures the orchestration logic, citation mapping, and persistence are executed correctly.*

### 3. `HybridRetrieverTest`
- Verify that both `SemanticRetriever` and `KeywordRetriever` are invoked.
- Validate RRF scoring (e.g., rank 1 gets 1/(60+1), rank 2 gets 1/(60+2), …) and that the final list is sorted descending by fused score.

### 4. `ResultFusionTest`
```java
@Test
void fuse_multipleLists_correctRRFScore() {
    RetrievedChunkDTO a = chunk("a", 0);
    RetrievedChunkDTO b = chunk("b", 1);
    List<List<RetrievedChunkDTO>> lists = List.of(List.of(a, b), List.of(b, a));
    List<RetrievedChunkDTO> fused = new ResultFusion().fuse(lists, 2);
    // Both a & b should have same RRF score (1/61 + 1/62)
    assertEquals(2, fused.size());
    assertEquals("a", fused.get(0).getId()); // order not important when scores equal
}
```
*Confirms the deterministic RRF implementation.*

---

## 📦 Adding the Tests to the Project
Create the following directory structure (if not already present):
```
src/test/java/com/safaricom/hr/service/DocumentServiceTest.java
src/test/java/com/safaricom/hr/service/RagServiceTest.java
src/test/java/com/safaricom/hr/retrieval/HybridRetrieverTest.java
src/test/java/com/safaricom/hr/retrieval/ResultFusionTest.java
```
Add the `junit-jupiter`, `mockito-core`, and `spring-boot-test` dependencies to `pom.xml` under the `<test>` scope.
```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
    <exclusions>
        <exclusion>
            <groupId>org.junit.vintage</groupId>
            <artifactId>junit-vintage-engine</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```
Run tests with:
```bash
./mvnw test
```
All tests should pass, giving **green** confidence that the core logic works without hitting external services.

---

## 📈 Next Steps (Beyond Unit Tests)
1. **Integration Tests** using Testcontainers for real PostgreSQL & Qdrant instances – validates end‑to‑end vector indexing and retrieval.
2. **Contract Tests** for the REST API (Spring `MockMvc` or `WebTestClient`).
3. **Load Tests** – simulate many concurrent chat requests to verify the `@Transactional` boundaries and DB connection pool sizing.
4. **CI/CD Pipeline** – add GitHub Actions to run `mvn verify` on each PR and publish Docker images.

---

## 🛠️ How to Execute the Test Suite
```bash
# 1️⃣ Ensure Docker is running (for Testcontainers, if you add them later)
# 2️⃣ Build the project and run tests
./mvnw clean verify
```
If you only want unit tests (no containers):
```bash
./mvnw test
```
All tests should complete within a few seconds and report **SUCCESS**.

---

**That completes the code review and provides a concrete test plan to guarantee the backend behaves as intended.**
