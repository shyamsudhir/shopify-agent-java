package com.app.supportspoc.util.shopify;

import com.app.supportspoc.dto.shopify.ShopifyCatalogDto.RefundSummary;
import com.app.supportspoc.dto.shopify.ShopifyCatalogDto.ReturnSummary;
import com.app.supportspoc.dto.shopify.ShopifyDto.GraphQLRequest;
import com.app.supportspoc.dto.shopify.ShopifyDto.GraphQLResponse;
import com.app.supportspoc.dto.shopify.ShopifyOrderContextDto.*;
import com.app.supportspoc.dto.shopify.ShopifyOrderContextResponseDto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ORDER_SUPPORT_CONTEXT, and -- as subsets of the SAME fetched object rather than
 * additional queries -- ORDER_FULFILLMENT_CONTEXT, ORDER_RETURN_CONTEXT, and
 * ORDER_REFUND_CONTEXT. This replaces the 3-call sequence from Phase 1/2
 * (getOrderDetails + getReturnStatus + getRefunds) with 1 call for anything that needs
 * the full support picture. checkCancelEligibility/checkReturnEligibility in the earlier
 * services still work as before and are unaffected -- use getOrderSupportContext when you
 * need the fuller picture (a support agent handling an open conversation), and the
 * lighter getOrderDetails when you only need order/fulfillment/tracking (a quick status
 * check) to avoid paying for transaction/refund/return data you won't use.
 */
@Service
public class ShopifyOrderContextService {

    private final ShopifyGraphQLClient client;

    @Autowired
    public ShopifyOrderContextService(ShopifyGraphQLClient client) {
        this.client = client;
    }

    private static final String ORDER_SUPPORT_CONTEXT_FIELDS =
            "id name createdAt displayFinancialStatus displayFulfillmentStatus " +
            "cancelledAt cancelReason closed fulfillable restockable refundable statusPageUrl " +
            "customer { id displayName email phone } " +
            "shippingAddress { address1 address2 city province zip countryCodeV2 } " +
            "billingAddress { address1 address2 city province zip countryCodeV2 } " +
            "totalPriceSet { shopMoney { amount currencyCode } } " +
            "lineItems(first: 25) { nodes { id title quantity currentQuantity sku " +
            "  originalUnitPriceSet { shopMoney { amount currencyCode } } " +
            "  product { id title } variant { id title } } } " +
            "fulfillments(first: 10) { id status displayStatus createdAt updatedAt " +
            "  estimatedDeliveryAt deliveredAt inTransitAt trackingInfo { company number url } } " +
            // ASSUMPTION: transactions and refunds are plain lists here, matching fulfillments --
            // see ShopifyReturnRefundService's getRefunds() javadoc for the same caveat on refunds.
            // Verify both via schema introspection before relying on this in production.
            "transactions(first: 10) { id kind status gateway createdAt amountSet { shopMoney { amount currencyCode } } } " +
            "refunds(first: 10) { id createdAt processedAt note totalRefundedSet { shopMoney { amount currencyCode } } } " +
            "returns(first: 10) { edges { node { id name status createdAt totalQuantity } } }";

    public OrderSupportContext getOrderSupportContext(String shopDomain, String accessToken, String orderName, String customerId)
            throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        String query = "query GetOrderSupportContext($filter: String!) { orders(first: 1, query: $filter) { edges { node { "
                + ORDER_SUPPORT_CONTEXT_FIELDS + " } } } }";

        String filter = buildOrderNameFilter(orderName, customerId);
        GraphQLRequest payload = new GraphQLRequest(query, Collections.singletonMap("filter", filter));

        OrderSupportContextData data = client.execute(endpoint, accessToken, payload,
                new TypeReference<GraphQLResponse<OrderSupportContextData>>() {});

        List<OrderSupportContextEdge> edges = data.getOrders().getEdges();
        if (edges.isEmpty()) {
            return null;
        }
        return mapContext(edges.get(0).getNode());
    }

    private OrderSupportContext mapContext(OrderSupportContextNode node) {
        OrderSupportContext context = new OrderSupportContext();
        context.setId(node.getId());
        context.setName(node.getName());
        context.setCreatedAt(node.getCreatedAt());
        context.setDisplayFinancialStatus(node.getDisplayFinancialStatus());
        context.setDisplayFulfillmentStatus(node.getDisplayFulfillmentStatus());
        context.setCancelledAt(node.getCancelledAt());
        context.setCancelReason(node.getCancelReason());
        context.setClosed(node.getClosed());
        context.setFulfillable(node.getFulfillable());
        context.setRestockable(node.getRestockable());
        context.setRefundable(node.getRefundable());
        context.setStatusPageUrl(node.getStatusPageUrl());
        context.setCustomer(node.getCustomer());
        context.setShippingAddress(node.getShippingAddress());
        context.setBillingAddress(node.getBillingAddress());
        context.setTotalPriceSet(node.getTotalPriceSet());
        context.setFulfillments(node.getFulfillments());

        List<LineItemContext> lineItems = new ArrayList<>();
        if (node.getLineItems() != null && node.getLineItems().getNodes() != null) {
            for (LineItemContextNode li : node.getLineItems().getNodes()) {
                LineItemContext item = new LineItemContext();
                item.setId(li.getId());
                item.setTitle(li.getTitle());
                item.setQuantity(li.getQuantity());
                item.setCurrentQuantity(li.getCurrentQuantity());
                item.setSku(li.getSku());
                item.setOriginalUnitPriceSet(li.getOriginalUnitPriceSet());
                if (li.getProduct() != null) {
                    item.setProductId(li.getProduct().getId());
                    item.setProductTitle(li.getProduct().getTitle());
                }
                if (li.getVariant() != null) {
                    item.setVariantId(li.getVariant().getId());
                    item.setVariantTitle(li.getVariant().getTitle());
                }
                lineItems.add(item);
            }
        }
        context.setLineItems(lineItems);

        List<TransactionContext> transactions = new ArrayList<>();
        if (node.getTransactions() != null) {
            for (TransactionNode t : node.getTransactions()) {
                TransactionContext tx = new TransactionContext();
                tx.setId(t.getId());
                tx.setKind(t.getKind());
                tx.setStatus(t.getStatus());
                tx.setGateway(t.getGateway());
                tx.setCreatedAt(t.getCreatedAt());
                tx.setAmountSet(t.getAmountSet());
                transactions.add(tx);
            }
        }
        context.setTransactions(transactions);

        List<RefundSummary> refunds = new ArrayList<>();
        if (node.getRefunds() != null) {
            for (RefundNode r : node.getRefunds()) {
                RefundSummary refund = new RefundSummary();
                refund.setId(r.getId());
                refund.setCreatedAt(r.getCreatedAt());
                refund.setProcessedAt(r.getProcessedAt());
                refund.setNote(r.getNote());
                refund.setTotalRefundedSet(r.getTotalRefundedSet());
                refunds.add(refund);
            }
        }
        context.setRefunds(refunds);

        List<ReturnSummary> returns = new ArrayList<>();
        if (node.getReturns() != null && node.getReturns().getEdges() != null) {
            for (ReturnEdge edge : node.getReturns().getEdges()) {
                ReturnNode r = edge.getNode();
                ReturnSummary ret = new ReturnSummary();
                ret.setId(r.getId());
                ret.setName(r.getName());
                ret.setStatus(r.getStatus());
                ret.setCreatedAt(r.getCreatedAt());
                ret.setTotalQuantity(r.getTotalQuantity());
                returns.add(ret);
            }
        }
        context.setReturns(returns);

        return context;
    }

    private String buildOrderNameFilter(String orderName, String customerId) {
        String normalizedName = orderName.startsWith("#") ? orderName : "#" + orderName;
        StringBuilder sb = new StringBuilder();
        sb.append("name:").append(normalizedName.replace("\"", ""));
        if (customerId != null && !customerId.isBlank()) {
            sb.append(" AND customer_id:").append(customerId.replace("\"", ""));
        }
        return sb.toString();
    }
}
