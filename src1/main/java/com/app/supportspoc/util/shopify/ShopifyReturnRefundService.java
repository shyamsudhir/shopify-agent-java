package com.app.supportspoc.util.shopify;

import com.supportspoc.shopify.dto.ShopifyCatalogDto.*;
import com.supportspoc.shopify.dto.ShopifyCatalogResponseDto.*;
import com.supportspoc.shopify.dto.ShopifyDto.GraphQLError;
import com.supportspoc.shopify.dto.ShopifyDto.GraphQLRequest;
import com.supportspoc.shopify.dto.ShopifyDto.GraphQLResponse;
import com.supportspoc.shopify.dto.ShopifyOrderDto.OrderDetail;
import com.supportspoc.shopify.dto.ShopifyOrderDto.UserError;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Returns & Refunds use cases: check_return_eligibility, get_return_policy (see
 * ShopifyPolicyService), get_returnable_items, create_return, return_reason,
 * return_status, refund_status, refund_details, request_refund,
 * check_refund_eligibility.
 *
 * Split into its own service (rather than folded into ShopifyGraphQLService) because
 * this domain is large enough on its own; it follows the exact same GraphQL execution
 * pattern as the existing service rather than introducing a shared abstraction.
 *
 * IMPORTANT — customer-facing scope decisions (see Important assumptions in the reply
 * for the full reasoning):
 *  - "Request refund" and "Create return" both go through the returnRequest mutation,
 *    NOT refundCreate. refundCreate is a direct, immediate financial mutation designed
 *    for merchant/staff use; returnRequest puts the return in REQUESTED status and
 *    requires merchant approval (returnApproveRequest/returnDeclineRequest) before any
 *    money moves. Wiring a customer-facing AI agent directly to refundCreate would skip
 *    that human checkpoint -- that's a merchant policy decision, not something to bake
 *    in silently. If the merchant wants "auto-approve and auto-refund", that should be
 *    an explicit, separately reviewed capability, not this method.
 *  - "Return reason" is not a separate tool -- it's a field on each return line item in
 *    create_return.
 */
@Service
public class ShopifyReturnRefundService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ShopifyGraphQLService orderService;

    @Autowired
    public ShopifyReturnRefundService(ShopifyGraphQLService orderService) {
        this.orderService = orderService;
    }

    /**
     * get_returnable_items. Requires the order's gid -- resolve it first via
     * ShopifyGraphQLService.getOrderDetails(...).getId().
     */
    public List<ReturnableFulfillmentLineItem> getReturnableItems(String shopDomain, String accessToken, String orderGid)
            throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String query = "query GetReturnableItems($orderId: ID!) { returnableFulfillments(orderId: $orderId, first: 25) { " +
                "edges { node { returnableFulfillmentLineItems(first: 25) { edges { node { " +
                "fulfillmentLineItem { id lineItem { id title sku } } quantity } } } } } } }";

        GraphQLRequest payload = new GraphQLRequest(query, Collections.singletonMap("orderId", orderGid));
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<ReturnableFulfillmentsData> response = objectMapper.readValue(
                responseBody, new TypeReference<GraphQLResponse<ReturnableFulfillmentsData>>() {});
        validateResponse(response);

        List<ReturnableFulfillmentLineItem> result = new ArrayList<>();
        for (ReturnableFulfillmentEdge fulfillmentEdge : response.getData().getReturnableFulfillments().getEdges()) {
            for (ReturnableFulfillmentLineItemEdge lineEdge :
                    fulfillmentEdge.getNode().getReturnableFulfillmentLineItems().getEdges()) {
                ReturnableFulfillmentLineItemNode node = lineEdge.getNode();
                ReturnableFulfillmentLineItem item = new ReturnableFulfillmentLineItem();
                item.setFulfillmentLineItemId(node.getFulfillmentLineItem().getId());
                item.setLineItemId(node.getFulfillmentLineItem().getLineItem().getId());
                item.setTitle(node.getFulfillmentLineItem().getLineItem().getTitle());
                item.setSku(node.getFulfillmentLineItem().getLineItem().getSku());
                item.setReturnableQuantity(node.getQuantity());
                result.add(item);
            }
        }
        return result;
    }

    /**
     * check_return_eligibility. Like check_cancel_eligibility, this is a heuristic:
     * "eligible" here means Shopify currently exposes at least one returnable line item
     * for this order. It does NOT independently enforce a return window or final-sale
     * exclusions -- those are supposed to be reflected in what returnableFulfillments
     * returns (via the merchant's configured Return Rules), but that filtering has been
     * unreliable for some merchants (e.g. final-sale items still appearing as
     * returnable) per Shopify's own developer community. Treat this as a starting
     * signal, not a guarantee, and keep a policy-layer check for final-sale/window
     * rules if your merchant relies on those.
     */
    public ReturnEligibilityResult checkReturnEligibility(String shopDomain, String accessToken, String orderName, String customerId)
            throws IOException, InterruptedException {
        OrderDetail order = orderService.getOrderDetails(shopDomain, accessToken, orderName, customerId);

        ReturnEligibilityResult result = new ReturnEligibilityResult();
        List<String> reasons = new ArrayList<>();

        if (order == null) {
            result.setEligible(false);
            reasons.add("Order not found for this customer.");
            result.setReasons(reasons);
            return result;
        }

        List<ReturnableFulfillmentLineItem> returnableItems = getReturnableItems(shopDomain, accessToken, order.getId());
        result.setReturnableItems(returnableItems);

        boolean eligible = !returnableItems.isEmpty();
        result.setEligible(eligible);
        if (!eligible) {
            reasons.add("No returnable fulfillment line items found. The order may be unfulfilled, " +
                    "already fully returned, or excluded by the merchant's return rules.");
        } else {
            reasons.add("Returnable items found. Still subject to the merchant's return window and policy.");
        }
        result.setReasons(reasons);
        return result;
    }

    /**
     * create_return (and request_refund -- see class javadoc). Creates a REQUESTED
     * return; the merchant must approve it before any refund is issued. Reason and
     * customer note travel per-line-item inside the input, not as separate calls.
     */
    public ReturnRequestResult createReturnRequest(String shopDomain, String accessToken, ReturnRequestInput input)
            throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String mutation = "mutation CreateReturnRequest($input: ReturnRequestInput!) { returnRequest(input: $input) { " +
                "return { id name status } userErrors { field message } } }";

        List<Map<String, Object>> returnLineItems = new ArrayList<>();
        for (ReturnLineItemRequest item : input.getLineItems()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("fulfillmentLineItemId", item.getFulfillmentLineItemId());
            line.put("quantity", item.getQuantity());
            if (item.getReason() != null) {
                line.put("returnReason", item.getReason());
            }
            if (item.getCustomerNote() != null) {
                line.put("customerNote", item.getCustomerNote());
            }
            returnLineItems.add(line);
        }

        Map<String, Object> inputMap = new LinkedHashMap<>();
        inputMap.put("orderId", input.getOrderId());
        inputMap.put("returnLineItems", returnLineItems);

        GraphQLRequest payload = new GraphQLRequest(mutation, Collections.singletonMap("input", inputMap));
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<ReturnRequestData> response = objectMapper.readValue(
                responseBody, new TypeReference<GraphQLResponse<ReturnRequestData>>() {});
        validateResponse(response);

        ReturnRequestPayload returnPayload = response.getData().getReturnRequest();
        ReturnRequestResult result = new ReturnRequestResult();

        boolean hasUserErrors = returnPayload.getUserErrors() != null && !returnPayload.getUserErrors().isEmpty();
        if (hasUserErrors) {
            result.setSubmitted(false);
            result.setErrors(returnPayload.getUserErrors().stream().map(UserError::getMessage).collect(Collectors.toList()));
        } else {
            result.setSubmitted(true);
            result.setReturnId(returnPayload.getReturnRecord().getId());
            result.setStatus(returnPayload.getReturnRecord().getStatus());
            result.setErrors(Collections.emptyList());
        }
        return result;
    }

    /** request_refund: same operation as create_return -- see class javadoc for why. */
    public ReturnRequestResult requestRefund(String shopDomain, String accessToken, ReturnRequestInput input)
            throws IOException, InterruptedException {
        return createReturnRequest(shopDomain, accessToken, input);
    }

    /** return_status. Lists all returns on the order (there can be more than one). */
    public List<ReturnSummary> getReturnStatus(String shopDomain, String accessToken, String orderName, String customerId)
            throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String query = "query GetReturnStatus($filter: String!) { orders(first: 1, query: $filter) { edges { node { " +
                "returns(first: 10) { edges { node { id name status createdAt totalQuantity } } } } } } }";

        String filter = buildOrderNameFilter(orderName, customerId);
        GraphQLRequest payload = new GraphQLRequest(query, Collections.singletonMap("filter", filter));
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<OrderReturnsData> response = objectMapper.readValue(
                responseBody, new TypeReference<GraphQLResponse<OrderReturnsData>>() {});
        validateResponse(response);

        List<OrderReturnsEdge> orderEdges = response.getData().getOrders().getEdges();
        if (orderEdges.isEmpty()) {
            return Collections.emptyList();
        }

        List<ReturnSummary> summaries = new ArrayList<>();
        for (ReturnSummaryEdge edge : orderEdges.get(0).getNode().getReturns().getEdges()) {
            ReturnSummaryNode node = edge.getNode();
            ReturnSummary summary = new ReturnSummary();
            summary.setId(node.getId());
            summary.setName(node.getName());
            summary.setStatus(node.getStatus());
            summary.setCreatedAt(node.getCreatedAt());
            summary.setTotalQuantity(node.getTotalQuantity());
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * refund_status + refund_details. One call serves both -- the transaction statuses
     * ARE the status, and the money breakdown IS the details.
     *
     * ASSUMPTION: Order.refunds is queried here as a plain list (like Order.fulfillments),
     * not a paginated connection with edges/nodes. Verify against your store's schema
     * (via introspection) before shipping -- if it's actually a connection, wrap the
     * "refunds(first: 10) { ... }" block in "{ edges { node { ... } } }" instead.
     */
    public List<RefundSummary> getRefunds(String shopDomain, String accessToken, String orderName, String customerId)
            throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String query = "query GetRefunds($filter: String!) { orders(first: 1, query: $filter) { edges { node { " +
                "refunds(first: 10) { id createdAt processedAt note " +
                "totalRefundedSet { shopMoney { amount currencyCode } } " +
                "transactions(first: 5) { nodes { status } } } } } } }";

        String filter = buildOrderNameFilter(orderName, customerId);
        GraphQLRequest payload = new GraphQLRequest(query, Collections.singletonMap("filter", filter));
        String responseBody = executePost(endpoint, accessToken, payload);

        GraphQLResponse<OrderRefundsData> response = objectMapper.readValue(
                responseBody, new TypeReference<GraphQLResponse<OrderRefundsData>>() {});
        validateResponse(response);

        List<OrderRefundsEdge> orderEdges = response.getData().getOrders().getEdges();
        if (orderEdges.isEmpty()) {
            return Collections.emptyList();
        }

        List<RefundSummary> summaries = new ArrayList<>();
        List<RefundNode> refundNodes = orderEdges.get(0).getNode().getRefunds();
        if (refundNodes != null) {
            for (RefundNode node : refundNodes) {
                RefundSummary summary = new RefundSummary();
                summary.setId(node.getId());
                summary.setCreatedAt(node.getCreatedAt());
                summary.setProcessedAt(node.getProcessedAt());
                summary.setNote(node.getNote());
                summary.setTotalRefundedSet(node.getTotalRefundedSet());
                if (node.getTransactions() != null && node.getTransactions().getNodes() != null) {
                    summary.setTransactionStatuses(node.getTransactions().getNodes().stream()
                            .map(OrderTransactionRef::getStatus)
                            .collect(Collectors.toList()));
                } else {
                    summary.setTransactionStatuses(Collections.emptyList());
                }
                summaries.add(summary);
            }
        }
        return summaries;
    }

    /**
     * check_refund_eligibility. Deliberately NOT a new query: Order.refundable (fetched
     * by ShopifyGraphQLService.getOrderDetails, see Phase 1) already answers this.
     * Call orderService.getOrderDetails(...) and read getRefundable() directly.
     */

    private String buildOrderNameFilter(String orderName, String customerId) {
        String normalizedName = orderName.startsWith("#") ? orderName : "#" + orderName;
        StringBuilder sb = new StringBuilder();
        sb.append("name:").append(normalizedName.replace("\"", ""));
        if (customerId != null && !customerId.isBlank()) {
            sb.append(" AND customer_id:").append(customerId.replace("\"", ""));
        }
        return sb.toString();
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
