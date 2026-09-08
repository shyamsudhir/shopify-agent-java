package com.app.supportspoc.slotfill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mock Shopify service layer: deterministic, fake responses for the 5 catalog intents,
 * standing in for real Admin GraphQL calls so the slot-filling flow can be exercised
 * end-to-end without live Shopify credentials. Swap the body of a method for a real
 * util/shopify call (see ShopifyGraphQLService/ShopifyReturnRefundService) when this
 * graduates past a demo.
 */
@Service
public class ShopifyMockService {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyMockService.class);

    public Map<String, Object> execute(String intent, Map<String, Object> entities) {
        logger.info("shopify_mock_execute - intent={}, entities={}", intent, entities);
        return switch (intent) {
            case IntentCatalog.FETCH_ORDER -> fetchOrder(entities);
            case IntentCatalog.FETCH_LAST_10_ORDERS -> fetchLast10Orders(entities);
            case IntentCatalog.FETCH_ORDERS_IN_DURATION -> fetchOrdersInDuration(entities);
            case IntentCatalog.CREATE_ORDER -> createOrder(entities);
            case IntentCatalog.CANCEL_ORDER -> cancelOrder(entities);
            default -> throw new IllegalArgumentException("no mock implementation for intent '" + intent + "'");
        };
    }

    private Map<String, Object> fetchOrder(Map<String, Object> entities) {
        String orderId = String.valueOf(entities.get("orderId"));

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", orderId);
        order.put("name", orderId.startsWith("#") ? orderId : "#" + orderId);
        order.put("financialStatus", "paid");
        order.put("fulfillmentStatus", "fulfilled");
        order.put("totalPrice", "129.99");
        order.put("currency", "USD");
        order.put("createdAt", Instant.now().minusSeconds(86_400).toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        return result;
    }

    private Map<String, Object> fetchLast10Orders(Map<String, Object> entities) {
        Object customerId = entities.get("customerId");
        int limit = entities.get("limit") instanceof Number n ? n.intValue() : 10;

        List<Map<String, Object>> orders = new ArrayList<>();
        for (int i = 1; i <= limit; i++) {
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("orderId", "MOCK-" + (1000 + i));
            order.put("name", "#" + (1000 + i));
            order.put("customerId", customerId);
            order.put("createdAt", Instant.now().minusSeconds(i * 3600L).toString());
            order.put("totalPrice", String.format("%.2f", 20 + i * 5.5));
            orders.add(order);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orders", orders);
        result.put("count", orders.size());
        return result;
    }

    private Map<String, Object> fetchOrdersInDuration(Map<String, Object> entities) {
        Object start = entities.get("start");
        Object end = entities.get("end");

        List<Map<String, Object>> orders = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("orderId", "MOCK-RANGE-" + i);
            order.put("name", "#" + (2000 + i));
            order.put("createdAt", start);
            orders.add(order);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orders", orders);
        result.put("count", orders.size());
        result.put("rangeStart", start);
        result.put("rangeEnd", end);
        return result;
    }

    private Map<String, Object> createOrder(Map<String, Object> entities) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", "gid://mock/Order/" + UUID.randomUUID());
        order.put("name", "#" + (9000 + (int) (Math.random() * 999)));
        order.put("customerId", entities.get("customerId"));
        order.put("lineItems", entities.get("lineItems"));
        order.put("shippingAddress", entities.get("shippingAddress"));
        order.put("financialStatus", entities.getOrDefault("financialStatus", "pending"));
        order.put("createdAt", Instant.now().toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        result.put("created", true);
        return result;
    }

    private Map<String, Object> cancelOrder(Map<String, Object> entities) {
        Map<String, Object> cancellation = new LinkedHashMap<>();
        cancellation.put("orderId", String.valueOf(entities.get("orderId")));
        cancellation.put("cancelled", true);
        cancellation.put("reason", entities.get("reason"));
        cancellation.put("emailNotificationSent", entities.getOrDefault("emailNotification", true));
        cancellation.put("cancelledAt", Instant.now().toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cancellation", cancellation);
        return result;
    }
}
