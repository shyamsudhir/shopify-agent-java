package com.app.supportspoc.util.shopify;

import com.app.supportspoc.dto.shopify.ShopifyCatalogDto.*;
import com.app.supportspoc.dto.shopify.ShopifyCatalogResponseDto.*;
import com.app.supportspoc.dto.shopify.ShopifyDto.GraphQLError;
import com.app.supportspoc.dto.shopify.ShopifyDto.GraphQLRequest;
import com.app.supportspoc.dto.shopify.ShopifyDto.GraphQLResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Product use cases: search by title/keyword/product type, filter by price/availability/
 * variant/color/size, product_details, product_variants, product_inventory, product_images,
 * product_recommendations.
 *
 * Extends the existing searchProducts(shopDomain, accessToken, keyword) in
 * ShopifyGraphQLService with structured filtering rather than replacing it -- that method
 * is left untouched.
 *
 * KNOWN GAP -- color/size/variant filtering: Shopify's Admin GraphQL search syntax
 * (the `query:` string on the products connection) does not support filtering by a
 * variant's option values (e.g. color=Red, size=M) -- confirmed via Shopify's own
 * developer community: the only variant-shaped filters available at the product level
 * are barcode, sku, variant_id, and variant_title. There is no server-side "color" or
 * "size" filter. Two ways to handle it, and I've implemented the second:
 *   1. Ask the merchant to tag products by color/size and filter with tag:Red -- fragile,
 *      depends on merchant tagging discipline.
 *   2. Fetch candidate products (by title/type/keyword) with their variants'
 *      selectedOptions, then filter in Java (filterVariantsByOption below). This is what
 *      "filter by variant/color/size" use cases should call after search_products /
 *      get_product_details -- it costs more rows fetched, not more requests.
 *
 * KNOWN GAP -- product_recommendations: the Admin API has no recommendation engine
 * (Shopify's `productRecommendations` field exists only on the Storefront API, which
 * this agent isn't using here). getProductRecommendations below is a same-product-type
 * heuristic, not a real recommendation -- label it as such to the customer if surfaced
 * verbatim ("customers who bought this also liked..." would overstate what this does).
 */
@Service
public class ShopifyProductService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * search_products: covers search by title, keyword, product type, price, availability,
     * and vendor in one flexible query -- covers search_by_title, search_by_keyword,
     * search_by_product_type, filter_by_price, filter_by_availability use cases.
     */
    public List<ProductSummary> searchProducts(String shopDomain, String accessToken, ProductSearchFilter filter)
            throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String query = "query SearchProducts($filter: String!, $first: Int!) { " +
                "products(first: $first, query: $filter) { edges { node { " +
                "id title productType status totalInventory " +
                "priceRangeV2 { minVariantPrice { amount currencyCode } maxVariantPrice { amount currencyCode } } " +
                "featuredImage { url altText } } } } }";

        int limit = filter.getLimit() == null ? 20 : Math.min(filter.getLimit(), 50);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("filter", buildProductFilter(filter));
        variables.put("first", limit);

        GraphQLRequest payload = new GraphQLRequest(query, variables);
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<ProductSearchData> response = objectMapper.readValue(
                responseBody, new TypeReference<GraphQLResponse<ProductSearchData>>() {});
        validateResponse(response);

        List<ProductSummary> results = new ArrayList<>();
        for (ProductSearchEdge edge : response.getData().getProducts().getEdges()) {
            ProductSearchNode node = edge.getNode();
            ProductSummary summary = new ProductSummary();
            summary.setId(node.getId());
            summary.setTitle(node.getTitle());
            summary.setProductType(node.getProductType());
            summary.setStatus(node.getStatus());
            summary.setTotalInventory(node.getTotalInventory());
            if (node.getPriceRangeV2() != null) {
                summary.setMinPrice(node.getPriceRangeV2().getMinVariantPrice() != null
                        ? node.getPriceRangeV2().getMinVariantPrice().getAmount() : null);
                summary.setMaxPrice(node.getPriceRangeV2().getMaxVariantPrice() != null
                        ? node.getPriceRangeV2().getMaxVariantPrice().getAmount() : null);
            }
            if (node.getFeaturedImage() != null) {
                summary.setFeaturedImageUrl(node.getFeaturedImage().getUrl());
            }
            results.add(summary);
        }
        return results;
    }

    /**
     * product_details + product_variants + product_inventory + product_images in one call --
     * they all live on the same Product node, so there's no reason to split them.
     */
    public ProductDetail getProductDetails(String shopDomain, String accessToken, String productGid)
            throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String query = "query GetProductDetails($id: ID!) { product(id: $id) { " + PRODUCT_DETAIL_FIELDS + " } }";

        GraphQLRequest payload = new GraphQLRequest(query, Collections.singletonMap("id", productGid));
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<ProductDetailData> response = objectMapper.readValue(
                responseBody, new TypeReference<GraphQLResponse<ProductDetailData>>() {});
        validateResponse(response);

        return mapProductDetail(response.getData().getProduct());
    }

    /** Same as getProductDetails, but for when the agent only has the product's handle/slug. */
    public ProductDetail getProductDetailsByHandle(String shopDomain, String accessToken, String handle)
            throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String query = "query GetProductDetailsByHandle($handle: String!) { productByHandle(handle: $handle) { "
                + PRODUCT_DETAIL_FIELDS + " } }";

        GraphQLRequest payload = new GraphQLRequest(query, Collections.singletonMap("handle", handle));
        String responseBody = executePost(endpoint, accessToken, payload);

        // productByHandle shares the Product shape, so it deserializes into the same wrapper
        // as long as the JSON key matches -- see note in the response DTO if the field name differs.
        Map<String, Object> raw = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
        GraphQLResponse<ProductDetailData> response = objectMapper.convertValue(
                remapKey(raw, "productByHandle", "product"), new TypeReference<GraphQLResponse<ProductDetailData>>() {});
        validateResponse(response);

        return mapProductDetail(response.getData().getProduct());
    }

    private static final String PRODUCT_DETAIL_FIELDS =
            "id title descriptionHtml productType vendor status totalInventory " +
            "images(first: 10) { nodes { url altText } } " +
            "variants(first: 50) { nodes { id title sku price availableForSale inventoryQuantity " +
            "  selectedOptions { name value } image { url altText } } }";

    private ProductDetail mapProductDetail(ProductDetailNode node) {
        if (node == null) {
            return null;
        }
        ProductDetail detail = new ProductDetail();
        detail.setId(node.getId());
        detail.setTitle(node.getTitle());
        detail.setDescriptionHtml(node.getDescriptionHtml());
        detail.setProductType(node.getProductType());
        detail.setVendor(node.getVendor());
        detail.setStatus(node.getStatus());
        detail.setTotalInventory(node.getTotalInventory());

        List<ProductImage> images = new ArrayList<>();
        if (node.getImages() != null && node.getImages().getNodes() != null) {
            for (ImageRef img : node.getImages().getNodes()) {
                ProductImage image = new ProductImage();
                image.setUrl(img.getUrl());
                image.setAltText(img.getAltText());
                images.add(image);
            }
        }
        detail.setImages(images);

        List<ProductVariantDetail> variants = new ArrayList<>();
        if (node.getVariants() != null && node.getVariants().getNodes() != null) {
            for (ProductVariantNode v : node.getVariants().getNodes()) {
                ProductVariantDetail variant = new ProductVariantDetail();
                variant.setId(v.getId());
                variant.setTitle(v.getTitle());
                variant.setSku(v.getSku());
                variant.setPrice(v.getPrice());
                variant.setAvailableForSale(v.getAvailableForSale());
                variant.setInventoryQuantity(v.getInventoryQuantity());
                variant.setImageUrl(v.getImage() != null ? v.getImage().getUrl() : null);
                if (v.getSelectedOptions() != null) {
                    variant.setSelectedOptions(v.getSelectedOptions().stream().map(o -> {
                        SelectedOption opt = new SelectedOption();
                        opt.setName(o.getName());
                        opt.setValue(o.getValue());
                        return opt;
                    }).collect(Collectors.toList()));
                }
                variants.add(variant);
            }
        }
        detail.setVariants(variants);

        return detail;
    }

    /**
     * filter_by_variant / filter_by_color / filter_by_size. Client-side filter over a
     * product's variants -- see class javadoc for why this can't be pushed into the
     * GraphQL query string. optionName is case-insensitive ("color", "Color", "size"...).
     */
    public List<ProductVariantDetail> filterVariantsByOption(List<ProductVariantDetail> variants, String optionName, String optionValue) {
        return variants.stream()
                .filter(v -> v.getSelectedOptions() != null && v.getSelectedOptions().stream()
                        .anyMatch(o -> o.getName().equalsIgnoreCase(optionName) && o.getValue().equalsIgnoreCase(optionValue)))
                .collect(Collectors.toList());
    }

    /**
     * product_recommendations -- heuristic only, see class javadoc. Finds other active
     * products of the same product type, excluding the product itself.
     */
    public List<ProductSummary> getProductRecommendations(String shopDomain, String accessToken, String productGid, int limit)
            throws IOException, InterruptedException {
        ProductDetail source = getProductDetails(shopDomain, accessToken, productGid);
        if (source == null || source.getProductType() == null || source.getProductType().isBlank()) {
            return Collections.emptyList();
        }

        ProductSearchFilter filter = new ProductSearchFilter();
        filter.setProductType(source.getProductType());
        filter.setLimit(limit + 1); // fetch one extra in case the source product is included

        return searchProducts(shopDomain, accessToken, filter).stream()
                .filter(p -> !p.getId().equals(productGid))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String buildProductFilter(ProductSearchFilter filter) {
        List<String> clauses = new ArrayList<>();

        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
            clauses.add(escapeFilterValue(filter.getKeyword())); // bare terms hit Shopify's default multi-field search
        }
        if (filter.getTitle() != null && !filter.getTitle().isBlank()) {
            clauses.add("title:" + escapeFilterValue(filter.getTitle()));
        }
        if (filter.getProductType() != null && !filter.getProductType().isBlank()) {
            clauses.add("product_type:" + escapeFilterValue(filter.getProductType()));
        }
        if (filter.getTag() != null && !filter.getTag().isBlank()) {
            clauses.add("tag:" + escapeFilterValue(filter.getTag()));
        }
        if (filter.getVendor() != null && !filter.getVendor().isBlank()) {
            clauses.add("vendor:" + escapeFilterValue(filter.getVendor()));
        }
        // Shopify's documented `price` filter example is exact-match only (price:100.57).
        // Range operators are used here by analogy with other numeric/date filters
        // (inventory_total, created_at) but are NOT confirmed for `price` -- test against
        // a real store before relying on this in production.
        if (filter.getPriceMin() != null && !filter.getPriceMin().isBlank()) {
            clauses.add("price:>=" + filter.getPriceMin());
        }
        if (filter.getPriceMax() != null && !filter.getPriceMax().isBlank()) {
            clauses.add("price:<=" + filter.getPriceMax());
        }
        if (Boolean.TRUE.equals(filter.getAvailableOnly())) {
            // Best-effort proxy for "in stock" -- see class javadoc. Not a perfect match
            // for "available for sale" (which also depends on inventory policy).
            clauses.add("out_of_stock_somewhere:false");
        }
        clauses.add("status:active");

        return String.join(" AND ", clauses);
    }

    private String escapeFilterValue(String value) {
        return value.replace("\"", "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> remapKey(Map<String, Object> raw, String from, String to) {
        Map<String, Object> data = (Map<String, Object>) raw.get("data");
        if (data != null && data.containsKey(from)) {
            data.put(to, data.remove(from));
        }
        return raw;
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
