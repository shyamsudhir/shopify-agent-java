package com.app.supportspoc.util.shopify;

import com.supportspoc.shopify.dto.ShopifyDto.*;
import com.supportspoc.shopify.dto.ShopifyOrderDto.*;
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

@Service
public class ShopifyGraphQLService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public List<OrderNode> getLastTenOrders(String shopDomain, String accessToken) throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
//        String query = "query GetLastTenOrders { orders(first: 10, sortKey: CREATED_AT, reverse: true) { edges { node { id name createdAt displayFinancialStatus displayFulfillmentStatus totalPriceSet { shopMoney { amount currencyCode } } customer { id displayName email phone } lineItems(first: 10) { nodes { title quantity originalUnitPriceSet { shopMoney { amount currencyCode } } } } } } } }";
        String query = "query GetLastTenOrders {\n" +
                "  orders(\n" +
                "    first: 10\n" +
                "    sortKey: CREATED_AT\n" +
                "    reverse: true\n" +
                "  ) {\n" +
                "    edges {\n" +
                "      node {\n" +
                "        id\n" +
                "        name\n" +
                "        createdAt\n" +
                "        displayFinancialStatus\n" +
                "        displayFulfillmentStatus\n" +
                "\n" +
                "        totalPriceSet {\n" +
                "          shopMoney {\n" +
                "            amount\n" +
                "            currencyCode\n" +
                "          }\n" +
                "        }\n" +
                "\n" +
                "        customer {\n" +
                "          id\n" +
                "          displayName\n" +
                "          email\n" +
                "          phone\n" +
                "        }\n" +
                "\n" +
                "        lineItems(first: 10) {\n" +
                "          nodes {\n" +
                "            title\n" +
                "            quantity\n" +
                "\n" +
                "            originalUnitPriceSet {\n" +
                "              shopMoney {\n" +
                "                amount\n" +
                "                currencyCode\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        GraphQLRequest payload = new GraphQLRequest(query, Collections.emptyMap());
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<OrdersData> response = objectMapper.readValue(
                responseBody,
                new TypeReference<GraphQLResponse<OrdersData>>() {}
        );

        validateResponse(response);

        return response.getData().getOrders().getEdges().stream()
                .map(OrderEdge::getNode)
                .collect(Collectors.toList());
    }

    public List<ProductNode> searchProducts(String shopDomain, String accessToken, String keyword) throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + ".myshopify.com/admin/api/2026-07/graphql.json";
        String query = "query SearchProducts($queryTerm: String!) { products(first: 10, query: $queryTerm) { edges { node { id title status totalInventory variants(first: 5) { nodes { id title sku price } } } } } }";

        GraphQLRequest payload = new GraphQLRequest(query, Collections.singletonMap("queryTerm", keyword));
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<ProductsData> response = objectMapper.readValue(
                responseBody,
                new TypeReference<GraphQLResponse<ProductsData>>() {}
        );

        validateResponse(response);

        return response.getData().getProducts().getEdges().stream()
                .map(ProductEdge::getNode)
                .collect(Collectors.toList());
    }

    // =====================================================================
    // Order Management use cases:
    //   get_order, get_order_status, get_fulfillment, get_tracking,
    //   get_delivery_estimate, search_orders, cancel_order,
    //   check_cancel_eligibility
    // =====================================================================

    private static final String ORDER_DETAIL_FIELDS =
            "id name createdAt displayFinancialStatus displayFulfillmentStatus " +
            "cancelledAt cancelReason closed fulfillable restockable refundable statusPageUrl " +
            "customer { id displayName email phone } " +
            "shippingAddress { address1 address2 city province zip countryCodeV2 } " +
            "totalPriceSet { shopMoney { amount currencyCode } } " +
            "lineItems(first: 25) { nodes { id title quantity currentQuantity sku " +
            "  originalUnitPriceSet { shopMoney { amount currencyCode } } } } " +
            "fulfillments(first: 10) { id status displayStatus createdAt updatedAt " +
            "  estimatedDeliveryAt deliveredAt inTransitAt trackingInfo { company number url } }";

    /**
     * Backs get_order, get_order_status, get_fulfillment, get_tracking, and
     * get_delivery_estimate. One query covers all five because they all read
     * from the same Order + its fulfillments -- there's no reason to make the
     * agent take five round trips for one order.
     *
     * customerId scopes the lookup to the authenticated customer so the tool
     * can't be used to look up someone else's order by guessing an order name.
     *
     * @param orderName customer-facing order identifier, e.g. "#1042" or "1042"
     * @param customerId numeric Shopify customer id (not gid) of the authenticated customer
     * @return the order, or null if not found / doesn't belong to this customer
     */
    public OrderDetail getOrderDetails(String shopDomain, String accessToken, String orderName, String customerId)
            throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String query = "query GetOrderDetails($filter: String!) { orders(first: 1, query: $filter) { edges { node { "
                + ORDER_DETAIL_FIELDS + " } } } }";

        String filter = buildOrderNameFilter(orderName, customerId);
        GraphQLRequest payload = new GraphQLRequest(query, Collections.singletonMap("filter", filter));
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<OrderDetailData> response = objectMapper.readValue(
                responseBody,
                new TypeReference<GraphQLResponse<OrderDetailData>>() {}
        );

        validateResponse(response);

        List<OrderDetailEdge> edges = response.getData().getOrders().getEdges();
        return edges.isEmpty() ? null : edges.get(0).getNode();
    }

    /**
     * search_orders: covers both "orders by date" and "orders by status" (and any
     * combination) via Shopify's query filter syntax -- one flexible query instead
     * of two near-identical methods.
     *
     * customerId is mandatory: without it this would let an agent enumerate every
     * order in the store, not just the caller's own orders.
     */
    public List<OrderSummary> searchOrders(String shopDomain, String accessToken, SearchOrdersFilter filter)
            throws IOException, InterruptedException {
        if (filter.getCustomerId() == null || filter.getCustomerId().isBlank()) {
            throw new IllegalArgumentException("customerId is required for search_orders");
        }

        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String query = "query SearchOrders($filter: String!, $first: Int!) { "
                + "orders(first: $first, query: $filter, sortKey: CREATED_AT, reverse: true) { "
                + "edges { node { id name createdAt displayFinancialStatus displayFulfillmentStatus closed "
                + "totalPriceSet { shopMoney { amount currencyCode } } } } } }";

        int limit = filter.getLimit() == null ? 20 : Math.min(filter.getLimit(), 50);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("filter", buildSearchOrdersFilter(filter));
        variables.put("first", limit);

        GraphQLRequest payload = new GraphQLRequest(query, variables);
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<OrderSummaryData> response = objectMapper.readValue(
                responseBody,
                new TypeReference<GraphQLResponse<OrderSummaryData>>() {}
        );

        validateResponse(response);

        return response.getData().getOrders().getEdges().stream()
                .map(OrderSummaryEdge::getNode)
                .collect(Collectors.toList());
    }

    /**
     * check_cancel_eligibility. This is a heuristic pre-check built only from data
     * Shopify exposes on the Order object (cancelledAt, closed, fulfillable,
     * restockable, per-fulfillment status). It intentionally does NOT encode
     * merchant policy (e.g. "no cancellations after 24 hours", "final sale items
     * excluded") -- Shopify has no such field, so that has to be layered on by
     * the business-rule / policy facade, not this method. The authoritative
     * answer is still whatever orderCancel returns via orderCancelUserErrors.
     */
    public CancelEligibilityResult checkCancelEligibility(String shopDomain, String accessToken, String orderName, String customerId)
            throws IOException, InterruptedException {
        OrderDetail order = getOrderDetails(shopDomain, accessToken, orderName, customerId);

        CancelEligibilityResult result = new CancelEligibilityResult();
        List<String> reasons = new ArrayList<>();

        if (order == null) {
            result.setEligible(false);
            reasons.add("Order not found for this customer.");
            result.setReasons(reasons);
            return result;
        }

        result.setOrder(order);

        if (order.getCancelledAt() != null) {
            reasons.add("Order was already cancelled on " + order.getCancelledAt() + ".");
        }
        if (Boolean.TRUE.equals(order.getClosed())) {
            reasons.add("Order is closed (fully fulfilled/cancelled and financially settled).");
        }
        boolean hasActiveShippedFulfillment = order.getFulfillments() != null && order.getFulfillments().stream()
                .anyMatch(f -> f.getStatus() != null && !"cancelled".equalsIgnoreCase(f.getStatus()));
        if (hasActiveShippedFulfillment) {
            reasons.add("Order has at least one active fulfillment; cancelling may require the merchant to " +
                    "handle the in-progress shipment.");
        }

        boolean eligible = reasons.isEmpty();
        result.setEligible(eligible);
        if (eligible) {
            reasons.add("No cancellation blockers found in Shopify order data. " +
                    "Still subject to merchant cancellation policy.");
        }
        result.setReasons(reasons);
        return result;
    }

    /**
     * cancel_order via the orderCancel mutation. This is an async operation on
     * Shopify's side (returns a job, not an immediate final state) -- do not
     * report the cancellation as complete to the customer until the job/order
     * status is confirmed on a follow-up read.
     *
     * Callers must resolve the order's gid (via getOrderDetails/checkCancelEligibility)
     * before calling this -- it does not accept a customer-facing order number.
     */
    public CancelOrderResult cancelOrder(String shopDomain, String accessToken, CancelOrderRequest request)
            throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String mutation = "mutation CancelOrder($orderId: ID!, $notifyCustomer: Boolean, " +
                "$refundMethod: OrderCancelRefundMethodInput!, $restock: Boolean!, $reason: OrderCancelReason!, $staffNote: String) { " +
                "orderCancel(orderId: $orderId, notifyCustomer: $notifyCustomer, refundMethod: $refundMethod, " +
                "restock: $restock, reason: $reason, staffNote: $staffNote) { " +
                "job { id done } orderCancelUserErrors { field message code } } }";

        Map<String, Object> refundMethod = request.getRefundMethod() != null
                ? request.getRefundMethod()
                : Map.of("originalPaymentMethodsRefund", Map.of());

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("orderId", request.getOrderId());
        variables.put("notifyCustomer", request.isNotifyCustomer());
        variables.put("refundMethod", refundMethod);
        variables.put("restock", request.isRestock());
        variables.put("reason", request.getReason());
        variables.put("staffNote", request.getStaffNote());

        GraphQLRequest payload = new GraphQLRequest(mutation, variables);
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<OrderCancelData> response = objectMapper.readValue(
                responseBody,
                new TypeReference<GraphQLResponse<OrderCancelData>>() {}
        );

        validateResponse(response);

        OrderCancelPayload cancelPayload = response.getData().getOrderCancel();
        CancelOrderResult result = new CancelOrderResult();

        boolean hasUserErrors = cancelPayload.getOrderCancelUserErrors() != null
                && !cancelPayload.getOrderCancelUserErrors().isEmpty();

        if (hasUserErrors) {
            result.setSubmitted(false);
            result.setErrors(cancelPayload.getOrderCancelUserErrors().stream()
                    .map(UserError::getMessage)
                    .collect(Collectors.toList()));
        } else {
            result.setSubmitted(true);
            result.setJobId(cancelPayload.getJob() != null ? cancelPayload.getJob().getId() : null);
            result.setJobDone(cancelPayload.getJob() != null && cancelPayload.getJob().isDone());
            result.setErrors(Collections.emptyList());
        }

        return result;
    }

    // ---------- filter builders ----------

    private String buildOrderNameFilter(String orderName, String customerId) {
        String normalizedName = orderName.startsWith("#") ? orderName : "#" + orderName;
        StringBuilder sb = new StringBuilder();
        sb.append("name:").append(escapeFilterValue(normalizedName));
        if (customerId != null && !customerId.isBlank()) {
            sb.append(" AND customer_id:").append(escapeFilterValue(customerId));
        }
        return sb.toString();
    }

    private String buildSearchOrdersFilter(SearchOrdersFilter filter) {
        List<String> clauses = new ArrayList<>();
        clauses.add("customer_id:" + escapeFilterValue(filter.getCustomerId()));

        if (filter.getFulfillmentStatus() != null && !filter.getFulfillmentStatus().isBlank()) {
            clauses.add("fulfillment_status:" + escapeFilterValue(filter.getFulfillmentStatus()));
        }
        if (filter.getFinancialStatus() != null && !filter.getFinancialStatus().isBlank()) {
            clauses.add("financial_status:" + escapeFilterValue(filter.getFinancialStatus()));
        }
        if (filter.getCreatedAfter() != null && !filter.getCreatedAfter().isBlank()) {
            clauses.add("created_at:>=" + escapeFilterValue(filter.getCreatedAfter()));
        }
        if (filter.getCreatedBefore() != null && !filter.getCreatedBefore().isBlank()) {
            clauses.add("created_at:<=" + escapeFilterValue(filter.getCreatedBefore()));
        }

        return String.join(" AND ", clauses);
    }

    private String escapeFilterValue(String value) {
        // Shopify search syntax treats quotes specially; strip them defensively
        // since these values may originate from customer-facing input.
        return value.replace("\"", "");
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