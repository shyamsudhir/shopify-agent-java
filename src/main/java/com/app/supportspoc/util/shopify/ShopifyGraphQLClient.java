package com.app.supportspoc.util.shopify;

import com.app.supportspoc.dto.shopify.ShopifyDto.GraphQLError;
import com.app.supportspoc.dto.shopify.ShopifyDto.GraphQLRequest;
import com.app.supportspoc.dto.shopify.ShopifyDto.GraphQLResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * GRAPHQL_CLIENT infra: query/mutation execution, HTTP + GraphQL error handling,
 * rate-limit handling (Shopify THROTTLED errors), retry with backoff, timeout,
 * request logging -- in one place instead of duplicated per service.
 *
 * NOT YET wired into the existing services (ShopifyGraphQLService, ShopifyProductService,
 * ShopifyReturnRefundService, ShopifyPolicyService) -- each still has its own private
 * executePost/validateResponse, copied from your original code's pattern. Migrating them
 * to use this client is a mechanical follow-up (swap the private helpers for a call to
 * execute(...)) but touches every existing file, so I left that as an explicit next step
 * rather than doing it silently inside this change.
 *
 * Retry scope: only retries what's safe to retry -- GraphQL THROTTLED errors (Shopify's
 * cost-based rate limiting) and transient HTTP 429/502/503/504. It does NOT retry other
 * 4xx/5xx or GraphQL errors that aren't THROTTLED, and it never retries mutations
 * automatically beyond this -- retrying a mutation blindly risks double-submitting a
 * write (e.g. a second orderCancel/returnRequest). Callers doing mutations should still
 * treat a timeout as "unknown outcome, go re-read the resource" rather than "safe to
 * retry the write."
 */
@Component
public class ShopifyGraphQLClient {

    private static final Logger log = LoggerFactory.getLogger(ShopifyGraphQLClient.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 500L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public <T> T execute(String endpoint, String accessToken, GraphQLRequest payload, TypeReference<GraphQLResponse<T>> typeRef)
            throws IOException, InterruptedException {
        int attempt = 0;
        while (true) {
            attempt++;
            long startedAt = System.currentTimeMillis();
            String responseBody;
            int statusCode;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-Shopify-Access-Token", accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = httpResponse.statusCode();
            responseBody = httpResponse.body();

            log.debug("Shopify GraphQL call to {} finished in {}ms with HTTP {} (attempt {}/{})",
                    endpoint, System.currentTimeMillis() - startedAt, statusCode, attempt, MAX_ATTEMPTS);

            if (isRetryableHttpStatus(statusCode) && attempt < MAX_ATTEMPTS) {
                backoff(attempt);
                continue;
            }
            if (statusCode != 200) {
                throw new RuntimeException("Shopify HTTP error [" + statusCode + "]: " + responseBody);
            }

            GraphQLResponse<T> response = objectMapper.readValue(responseBody, typeRef);

            if (isThrottled(response) && attempt < MAX_ATTEMPTS) {
                log.warn("Shopify GraphQL call to {} was throttled, retrying (attempt {}/{})", endpoint, attempt, MAX_ATTEMPTS);
                backoff(attempt);
                continue;
            }

            validate(response);
            return response.getData();
        }
    }

    private boolean isRetryableHttpStatus(int statusCode) {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private <T> boolean isThrottled(GraphQLResponse<T> response) {
        if (response.getErrors() == null) {
            return false;
        }
        return response.getErrors().stream()
                .map(GraphQLError::getMessage)
                .anyMatch(message -> message != null && message.toUpperCase().contains("THROTTLED"));
    }

    private void backoff(int attempt) throws InterruptedException {
        long delay = BASE_BACKOFF_MILLIS * (1L << (attempt - 1)); // 500ms, 1000ms, 2000ms...
        Thread.sleep(delay);
    }

    private <T> void validate(GraphQLResponse<T> response) {
        if (response.getErrors() != null && !response.getErrors().isEmpty()) {
            String errors = response.getErrors().stream()
                    .map(GraphQLError::getMessage)
                    .collect(Collectors.joining("; "));
            log.error("Shopify GraphQL errors: {}", errors);
            throw new RuntimeException("Shopify GraphQL errors: " + errors);
        }
        if (response.getData() == null) {
            throw new RuntimeException("Shopify GraphQL response returned empty data.");
        }
    }
}
