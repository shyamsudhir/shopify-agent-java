package com.app.supportspoc.dto.shopify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class ShopifyDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GraphQLRequest {
        private String query;
        private Map<String, Object> variables;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GraphQLResponse<T> {
        private T data;
        private List<GraphQLError> errors;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GraphQLError {
        private String message;
    }

    // --- Order DTOs ---
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrdersData {
        private OrdersConnection orders;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrdersConnection {
        private List<OrderEdge> edges;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderEdge {
        private OrderNode node;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderNode {
        private String id;
        private String name;
        private String createdAt;
        private String displayFinancialStatus;
        private String displayFulfillmentStatus;
        private PriceSet totalPriceSet;
        private Customer customer;
        private LineItemConnection lineItems;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceSet {
        private Money shopMoney;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Money {
        private String amount;
        private String currencyCode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Customer {
        private String id;
        private String displayName;
        private String email;
        private String phone;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LineItemConnection {
        private List<LineItemNode> nodes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LineItemNode {
        private String title;
        private int quantity;
        private PriceSet originalUnitPriceSet;
    }

    // --- Product DTOs ---
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductsData {
        private ProductsConnection products;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductsConnection {
        private List<ProductEdge> edges;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductEdge {
        private ProductNode node;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductNode {
        private String id;
        private String title;
        private String status;
        private Integer totalInventory;
        private VariantConnection variants;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VariantConnection {
        private List<VariantNode> nodes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VariantNode {
        private String id;
        private String title;
        private String sku;
        private String price;
    }
}