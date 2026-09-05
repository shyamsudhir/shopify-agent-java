package com.app.supportspoc.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Component
public class LlmClient {

    private OpenAIClient openAIClient ;

    ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(LlmClient.class);
    @Value("${openai.api.key}") String apiKey;
    @Value("${app.embedding-model:text-embedding-3-small}")
    private String embeddingModel;
//    private final OpenAIClient deepSeekClient = OpenAIOkHttpClient.builder()
//            .apiKey(System.getenv("DEEPSEEK_API_KEY"))
//            .baseUrl("https://api.deepseek.com")
//            .build();
//    private final Client geminiClient = Client.builder()
//            .apiKey(System.getenv("GOOGLE_API_KEY"))
//    .model("gemini-2.5-flash-lite")
//            .build();
    public LlmClient() {
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }
    public String openAIChat(String message) {

        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(String.valueOf(ChatModel.GPT_5_NANO))
                .input(message)
                .build();

        Response response = openAIClient.responses().create(params);

        return response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message1 -> message1.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(ResponseOutputText::text)
                .collect(Collectors.joining());
    }

    public <T> CompletableFuture<T> callLLM(
            String model,
            String systemPrompt,
            String userPrompt,
            Class<T> responseType
    ) {
        logger.info("Calling LLM with model: {}, systemPrompt: {}, userPrompt: {}", model, systemPrompt, userPrompt);
        return CompletableFuture.supplyAsync(() -> {
            try {
                ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                        .model(model != null && !model.isBlank() ? model : "gpt-4o-mini")
                        .addSystemMessage(systemPrompt  + "\nReturn the response as valid JSON.")
                        .addUserMessage(userPrompt)
                        .responseFormat(ResponseFormatJsonObject.builder().build()) // Enforce JSON response
                        .build();

                ChatCompletion completion = openAIClient.chat().completions().create(params);

                if (completion.choices().isEmpty()) {
                    logger.warn("OpenAI returned no choices.");
                    return null;
                }

                String content = completion.choices().getFirst().message().content().orElse("");
                if (content.isBlank()) {
                    logger.warn("OpenAI returned empty message content.");
                    return null;
                }
                logger.info("Raw LLM response content: {}", content);
                return objectMapper.readValue(content, responseType);

            } catch (JsonProcessingException e) {
                logger.error("Failed to deserialize LLM JSON response to {}", responseType.getSimpleName(), e);
                return null;
            } catch (Exception e) {
                logger.error("Error during parseStructured LLM execution", e);
                throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Standard text chat completion.
     */
    public CompletableFuture<String> chatCompletion(String model, String systemPrompt, String userPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                        .model(model != null && !model.isBlank() ? model : "gpt-4.1-nano")
                        .addSystemMessage(systemPrompt)
                        .addUserMessage(userPrompt)
                        .build();

                ChatCompletion completion = openAIClient.chat().completions().create(params);

                return completion.choices().stream()
                        .findFirst()
                        .flatMap(choice -> choice.message().content())
                        .orElse("");
            } catch (Exception e) {
                logger.error("Error during chatCompletion LLM execution", e);
                throw new RuntimeException("LLM chat completion failed: " + e.getMessage(), e);
            }
        });
    }

    public List<float[]> getEmbedding(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                    .model(embeddingModel != null && !embeddingModel.isBlank() ? embeddingModel : "text-embedding-3-small")
                    .input(EmbeddingCreateParams.Input.ofArrayOfStrings(texts))
                    .build();

            CreateEmbeddingResponse response = openAIClient.embeddings().create(params);
            List<float[]> embeddings = new ArrayList<>();

            for (Embedding item : response.data()) {
                List<Float> vectorValues = item.embedding();
                float[] vector = new float[vectorValues.size()];
                for (int i = 0; i < vectorValues.size(); i++) {
                    vector[i] = vectorValues.get(i).floatValue();
                }
                embeddings.add(vector);
            }

            return embeddings;
        } catch (Exception e) {
            logger.error("Failed to generate embeddings: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding generation failed: " + e.getMessage(), e);
        }
    }
//    public String deepSeekChat(String message) {
//        ResponseCreateParams params = ResponseCreateParams.builder()
//                .model("deepseek-v4-flash")
//                .input(message)
//                .build();
//
//        Response response = deepSeekClient.responses().create(params);
//
//        return response.output().stream()
//                .flatMap(item -> item.message().stream())
//                .flatMap(message1 -> message1.content().stream())
//                .flatMap(content -> content.outputText().stream())
//                .map(ResponseOutputText::text)
//                .collect(Collectors.joining());
//    }
//
//    public String geminiChat(String message) {
//
//        GenerateContentResponse response =
//                geminiClient.models.generateContent(
//                        "gemini-2.5-flash",
//                        message,
//                        null
//                );
//
//        return response.text();
//    }

}
