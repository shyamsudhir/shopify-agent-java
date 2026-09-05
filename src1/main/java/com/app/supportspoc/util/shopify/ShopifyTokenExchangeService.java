package com.app.supportspoc.util.shopify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ShopifyTokenExchangeService {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Exchanges a Session Token (shp_ss_...) for an Admin API Access Token (shpat_... or shpca_...)
     *
     * @param shopDomain   e.g., "your-store.myshopify.com"
     * @param apiKey       Your Shopify App Client ID / API Key
     * @param apiSecret    Your Shopify App Client Secret
     * @param sessionToken The token starting with shp_ss_
     * @param isOnline     true for user-bound online token, false for store-level offline token
     * @return Admin API Access Token
     */
    public static String exchangeSessionTokenForAccessToken(
            String shopDomain,
            String apiKey,
            String apiSecret,
            String sessionToken,
            boolean isOnline) throws IOException, InterruptedException {

        String url = String.format("https://%s/admin/oauth/access_token", shopDomain);

        String requestedTokenType = isOnline
                ? "urn:shopify:params:oauth:token-type:online-access-token"
                : "urn:shopify:params:oauth:token-type:offline-access-token";

        // JSON payload for Shopify Token Exchange
        String requestBody = objectMapper.createObjectNode()
                .put("client_id", apiKey)
                .put("client_secret", apiSecret)
                .put("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                .put("subject_token", sessionToken)
                .put("subject_token_type", "urn:ietf:params:oauth:token-type:id-token")
                .put("requested_token_type", requestedTokenType)
                .toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode jsonNode = objectMapper.readTree(response.body());
            return jsonNode.get("access_token").asText();
        } else {
            throw new RuntimeException("Token exchange failed [" + response.statusCode() + "]: " + response.body());
        }
    }
}