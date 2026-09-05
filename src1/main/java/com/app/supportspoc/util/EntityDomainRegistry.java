package com.app.supportspoc.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;

@Component
public class EntityDomainRegistry {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Stores: domain -> (entityKey -> embeddingVector)
    private final Map<String, Map<String, float[]>> domainEntityEmbeddings = new HashMap<>();

    // Stores: entityKey -> domain (for reverse lookups)
    private final Map<String, String> entityDomainRegistry = new HashMap<>();

    public EntityDomainRegistry(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @PostConstruct
    public void initializeIndex() {
        try {
            ClassPathResource resource = new ClassPathResource("entities.json");
            try (InputStream inputStream = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(inputStream);
                Iterator<Map.Entry<String, JsonNode>> domains = root.fields();

                while (domains.hasNext()) {
                    Map.Entry<String, JsonNode> domainEntry = domains.next();
                    String domain = domainEntry.getKey();
                    JsonNode domainNode = domainEntry.getValue();

                    // Navigate inside the "entities" object wrapper
                    JsonNode entitiesNode = domainNode.get("entities");
                    if (entitiesNode == null || !entitiesNode.isObject()) {
                        continue;
                    }

                    Map<String, float[]> entityVectors = new HashMap<>();
                    Iterator<Map.Entry<String, JsonNode>> entityFields = entitiesNode.fields();

                    while (entityFields.hasNext()) {
                        Map.Entry<String, JsonNode> entityEntry = entityFields.next();
                        String entityKey = entityEntry.getKey();

                        // entityEntry.getValue() retrieves the actual description string node
                        String description = entityEntry.getValue().asText();

                        // Pre-compute embedding at startup
                        float[] vector = llmClient.getEmbedding(description);
                        entityVectors.put(entityKey, vector);
                        entityDomainRegistry.put(entityKey, domain);
                    }
                    domainEntityEmbeddings.put(domain, entityVectors);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load and initialize entity vector index from entities.json", e);
        }
    }

//    public MatchResult findBestMatch(String domain, String queryText) {
//        Map<String, float[]> candidates = domainEntityEmbeddings.get(domain);
//        if (candidates == null || candidates.isEmpty()) return null;
//
//        float[] queryVector = llmClient.getEmbedding(queryText);
//        String bestKey = null;
//        double maxScore = -1.0;
//
//        for (Map.Entry<String, float[]> entry : candidates.entrySet()) {
//            double score = cosineSimilarity(queryVector, entry.getValue());
//            if (score > maxScore) {
//                maxScore = score;
//                bestKey = entry.getKey();
//            }
//        }
//        return new MatchResult(bestKey, maxScore, entityDomainRegistry.get(bestKey));
//    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }



    public record MatchResult(String entityKey, double score, String domain) {}
}
