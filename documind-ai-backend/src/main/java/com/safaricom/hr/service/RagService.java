package com.safaricom.hr.service;

import com.safaricom.hr.dto.ChatRequest;
import com.safaricom.hr.dto.ChatResponse;
import com.safaricom.hr.dto.CitationDTO;
import com.safaricom.hr.dto.RetrievedChunkDTO;
import com.safaricom.hr.entity.ConversationEntity;
import com.safaricom.hr.entity.MessageEntity;
import com.safaricom.hr.repository.ConversationRepository;
import com.safaricom.hr.repository.MessageRepository;
import com.safaricom.hr.retrieval.HybridRetriever;
import com.safaricom.hr.retrieval.Reranker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final QueryRewriteService queryRewriteService;
    private final HybridRetriever hybridRetriever;
    private final Reranker reranker;
    private final ContextBuilder contextBuilder;
    private final ChatClient chatClient;

    public RagService(ConversationRepository conversationRepository,
                      MessageRepository messageRepository,
                      QueryRewriteService queryRewriteService,
                      HybridRetriever hybridRetriever,
                      Reranker reranker,
                      ContextBuilder contextBuilder,
                      ChatClient.Builder chatClientBuilder) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.queryRewriteService = queryRewriteService;
        this.hybridRetriever = hybridRetriever;
        this.reranker = reranker;
        this.contextBuilder = contextBuilder;
        // Setting a system prompt at the client level ensures grounding rules are always applied
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        You are a strict, highly professional document question-answering assistant.
                        Answer the user's question using ONLY the supplied context.
                        Do not use outside knowledge.
                        
                        If the supplied context does not contain enough information to answer the question,
                        explicitly say: "I couldn't find enough information in the uploaded documents to answer this question reliably."
                        
                        Do not invent facts.
                        Every factual claim must be supported by the provided sources.
                        When referencing information, cite the document name and page number as seen in the context blocks.
                        """)
                .build();
    }

    @Transactional
    public ChatResponse askQuestion(ChatRequest request) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 1. Conversation Context Handling
        ConversationEntity conversation;
        List<MessageEntity> history = new ArrayList<>();
        
        if (request.getConversationId() != null) {
            conversation = conversationRepository.findById(request.getConversationId())
                    .orElseGet(() -> conversationRepository.save(new ConversationEntity()));
            history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        } else {
            conversation = conversationRepository.save(new ConversationEntity());
        }

        // Save User Message
        MessageEntity userMsg = MessageEntity.builder()
                .conversation(conversation)
                .role("USER")
                .content(request.getQuestion())
                .build();
        messageRepository.save(userMsg);

        // 2. Query Rewriting
        String rewrittenQuery = queryRewriteService.rewriteQuery(request.getQuestion(), history);

        // 3. Retrieval
        // In a real scenario we could extract metadata filters from the prompt. Here we pass null.
        List<RetrievedChunkDTO> retrievedCandidates = hybridRetriever.retrieve(rewrittenQuery, 20, null);

        // 4. Reranking
        List<RetrievedChunkDTO> rerankedChunks = reranker.rerank(rewrittenQuery, retrievedCandidates, 6);

        // 5. Context Construction
        String contextText = contextBuilder.buildContext(rerankedChunks);

        // 6. Grounded Answer Generation
        log.info("Generating answer using LLM...");
        String userPrompt = String.format("Context:\n%s\n\nQuestion: %s", contextText, request.getQuestion());
        
        String answer = chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();

        // Save AI Message
        MessageEntity aiMsg = MessageEntity.builder()
                .conversation(conversation)
                .role("ASSISTANT")
                .content(answer)
                .build();
        messageRepository.save(aiMsg);

        // 7. Citation Mapping
        // Map the retrieved chunks into DTOs.
        // Even if the LLM didn't use all of them, they represent the evidence base for this answer.
        List<CitationDTO> citations = rerankedChunks.stream()
                .map(chunk -> CitationDTO.builder()
                        .documentId((String) chunk.getMetadata().get("documentId"))
                        .documentName((String) chunk.getMetadata().getOrDefault("documentName", "Unknown"))
                        .pageNumber((Integer) chunk.getMetadata().get("page_number"))
                        .chunkId(chunk.getId())
                        .build())
                .collect(Collectors.toList());

        stopWatch.stop();

        // Prepare Metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("conversationId", conversation.getId());
        metadata.put("rewrittenQuery", rewrittenQuery);
        metadata.put("retrievedChunksCount", retrievedCandidates.size());
        metadata.put("rerankedChunksCount", rerankedChunks.size());
        metadata.put("latencyMs", stopWatch.getTotalTimeMillis());

        return ChatResponse.builder()
                .answer(answer)
                .citations(citations)
                .metadata(metadata)
                .build();
    }
}
