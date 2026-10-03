package com.safaricom.hr.retrieval;

import com.safaricom.hr.dto.RetrievedChunkDTO;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ResultFusion {

    // The k constant in RRF algorithm. Usually 60.
    private static final int RRF_K = 60;

    /**
     * Fuses multiple ranked lists using Reciprocal Rank Fusion (RRF).
     */
    public List<RetrievedChunkDTO> fuse(List<List<RetrievedChunkDTO>> rankedLists, int topK) {
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, RetrievedChunkDTO> chunkMap = new HashMap<>();

        for (List<RetrievedChunkDTO> rankedList : rankedLists) {
            for (int rank = 0; rank < rankedList.size(); rank++) {
                RetrievedChunkDTO chunk = rankedList.get(rank);
                String chunkId = chunk.getId();
                
                chunkMap.putIfAbsent(chunkId, chunk);

                double currentScore = rrfScores.getOrDefault(chunkId, 0.0);
                // RRF Formula: 1 / (k + rank)
                // rank is 0-indexed, so we add 1.
                double rrfContribution = 1.0 / (RRF_K + rank + 1);
                
                rrfScores.put(chunkId, currentScore + rrfContribution);
            }
        }

        // Apply fused scores and sort
        List<RetrievedChunkDTO> fusedResults = chunkMap.values().stream()
                .peek(chunk -> chunk.setScore(rrfScores.get(chunk.getId())))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());

        return fusedResults;
    }
}
