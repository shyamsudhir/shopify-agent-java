package com.app.supportspoc.util.shopify;

import com.supportspoc.shopify.dto.ShopifyCatalogDto.ShopPolicy;
import com.supportspoc.shopify.dto.ShopifyCatalogDto.StoreAddress;
import com.supportspoc.shopify.dto.ShopifyCatalogResponseDto.*;
import com.supportspoc.shopify.dto.ShopifyDto.GraphQLError;
import com.supportspoc.shopify.dto.ShopifyDto.GraphQLRequest;
import com.supportspoc.shopify.dto.ShopifyDto.GraphQLResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Store/product policy use cases.
 *
 * IMPLEMENTED (backed by real Shopify data via Shop.shopPolicies / Shop.billingAddress):
 *   get_return_policy, get_shipping_policy, get_contact_information, get_store_location
 *
 * NOT IMPLEMENTED -- and deliberately not stubbed with a fake GraphQL call, per the
 * "don't invent fields" and "never fabricate eligibility/behavior" ground rules:
 *   get_store_hours     -- Shopify's Admin API has no concept of storefront/support
 *                           "hours". A physical retail Location has an address but no
 *                           hours field. If you need this, source it from a metafield
 *                           on the Shop/Location, or your own merchant KB/CMS, and
 *                           expose it as a tool backed by THAT source, not GraphQL.
 *   get_exchange_policy  -- ShopPolicyType has no EXCHANGE_POLICY value (confirmed
 *                           against the current enum: CONTACT_INFORMATION, LEGAL_NOTICE,
 *                           PRIVACY_POLICY, REFUND_POLICY, SHIPPING_POLICY,
 *                           SUBSCRIPTION_POLICY, TERMS_OF_SALE, TERMS_OF_SERVICE).
 *                           Many merchants fold exchange terms into REFUND_POLICY --
 *                           worth checking that body text -- but there's no dedicated
 *                           field to query.
 *   get_payment_policy   -- same: no such ShopPolicyType. Payment methods/surcharges are
 *                           configured in Shopify Payments settings, which isn't exposed
 *                           as a readable "policy" via this API.
 *   get_warranty_policy  -- same: no such ShopPolicyType. Warranty terms, if the merchant
 *                           publishes them, are usually just prose somewhere (a metafield,
 *                           a CMS page, or folded into TERMS_OF_SALE) -- there's no
 *                           first-class Shopify object for it.
 *
 * For all four gaps above: if the merchant needs the agent to answer these, the right
 * fix is a merchant-maintained knowledge source (metafields, a CMS page, or a simple
 * policy config table your own backend owns) -- not a Shopify GraphQL call, because
 * Shopify doesn't model this data.
 */
@Service
public class ShopifyPolicyService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Fetches all shop policies in one call (there are at most 8, so this is cheap) and
     * is the basis for get_return_policy, get_shipping_policy, and get_contact_information
     * below -- no reason to make three round trips for three fields on the same object.
     */
    public List<ShopPolicy> getShopPolicies(String shopDomain, String accessToken) throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String query = "query GetShopPolicies { shop { shopPolicies { type title body url } } }";

        GraphQLRequest payload = new GraphQLRequest(query, Collections.emptyMap());
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<ShopData> response = objectMapper.readValue(
                responseBody, new TypeReference<GraphQLResponse<ShopData>>() {});
        validateResponse(response);

        List<ShopPolicy> policies = new ArrayList<>();
        List<ShopPolicyNode> nodes = response.getData().getShop().getShopPolicies();
        if (nodes != null) {
            for (ShopPolicyNode node : nodes) {
                ShopPolicy policy = new ShopPolicy();
                policy.setType(node.getType());
                policy.setTitle(node.getTitle());
                policy.setBodyHtml(node.getBody());
                policy.setUrl(node.getUrl());
                policies.add(policy);
            }
        }
        return policies;
    }

    /** get_return_policy */
    public Optional<ShopPolicy> getReturnPolicy(String shopDomain, String accessToken) throws IOException, InterruptedException {
        return findPolicyByType(shopDomain, accessToken, "REFUND_POLICY");
    }

    /** get_shipping_policy */
    public Optional<ShopPolicy> getShippingPolicy(String shopDomain, String accessToken) throws IOException, InterruptedException {
        return findPolicyByType(shopDomain, accessToken, "SHIPPING_POLICY");
    }

    /**
     * get_contact_information. Note: this is the merchant's CONTACT_INFORMATION policy
     * page (a free-text page many merchants never fill in), not necessarily a phone
     * number/support email in a structured field. If it comes back empty, fall back to
     * getStoreAddress's phone field, or a merchant KB.
     */
    public Optional<ShopPolicy> getContactInformation(String shopDomain, String accessToken) throws IOException, InterruptedException {
        return findPolicyByType(shopDomain, accessToken, "CONTACT_INFORMATION");
    }

    private Optional<ShopPolicy> findPolicyByType(String shopDomain, String accessToken, String type)
            throws IOException, InterruptedException {
        return getShopPolicies(shopDomain, accessToken).stream()
                .filter(p -> type.equals(p.getType()))
                .findFirst();
    }

    /**
     * get_store_location: the shop's registered business address. For a store with
     * physical retail locations (as opposed to the legal/billing address), query the
     * `locations` connection instead -- that's a different, bigger data model
     * (Location has its own address, fulfillsOnlineOrders flag, etc.) and wasn't in
     * scope here since the use case list didn't distinguish "which location."
     */
    public StoreAddress getStoreAddress(String shopDomain, String accessToken) throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String query = "query GetStoreAddress { shop { billingAddress { address1 address2 city province zip countryCodeV2 phone } } }";

        GraphQLRequest payload = new GraphQLRequest(query, Collections.emptyMap());
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<ShopData> response = objectMapper.readValue(
                responseBody, new TypeReference<GraphQLResponse<ShopData>>() {});
        validateResponse(response);

        ShopAddressNode node = response.getData().getShop().getBillingAddress();
        if (node == null) {
            return null;
        }
        StoreAddress address = new StoreAddress();
        address.setAddress1(node.getAddress1());
        address.setAddress2(node.getAddress2());
        address.setCity(node.getCity());
        address.setProvince(node.getProvince());
        address.setZip(node.getZip());
        address.setCountryCodeV2(node.getCountryCodeV2());
        address.setPhone(node.getPhone());
        return address;
    }

    private String executePost(String endpoint, String accessToken, GraphQLRequest payload) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("X-Shopify-Access-Token", accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Shopify HTTP error [" + response.statusCode() + "]: " + response.body());
        }

        return response.body();
    }

    private <T> void validateResponse(GraphQLResponse<T> response) {
        if (response.getErrors() != null && !response.getErrors().isEmpty()) {
            String errors = response.getErrors().stream()
                    .map(GraphQLError::getMessage)
                    .collect(Collectors.joining("; "));
            throw new RuntimeException("Shopify GraphQL errors: " + errors);
        }
        if (response.getData() == null) {
            throw new RuntimeException("Shopify GraphQL response returned empty data.");
        }
    }
}
