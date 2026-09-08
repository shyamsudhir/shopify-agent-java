package com.app.supportspoc.slotfill;

import com.app.supportspoc.slotfill.SlotFillingModels.EntitySpec;
import com.app.supportspoc.slotfill.SlotFillingModels.IntentSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fixed 5-intent taxonomy this module understands, encoded directly from the product
 * spec. An entity is marked non-required where the spec's own description says so
 * ("Optional...", "Fixed to...") or where a sane system default exists that a customer
 * would never naturally be asked for in conversation (financialStatus, emailNotification)
 * -- see the inline note on each one for the reasoning.
 *
 * Pure data, no Spring wiring -- IntentSpec/EntitySpec are plain records, so this is a
 * static registry rather than an injectable bean.
 */
public final class IntentCatalog {

    private IntentCatalog() {}

    public static final String FETCH_ORDER = "fetch_order";
    public static final String FETCH_LAST_10_ORDERS = "fetch_last_10_orders";
    public static final String FETCH_ORDERS_IN_DURATION = "fetch_orders_in_duration";
    public static final String CREATE_ORDER = "create_order";
    public static final String CANCEL_ORDER = "cancel_order";

    private static final Map<String, IntentSpec> INTENTS = build();

    public static IntentSpec get(String intentName) {
        String key = normalize(intentName);
        return key == null ? null : INTENTS.get(key);
    }

    public static boolean contains(String intentName) {
        return get(intentName) != null;
    }

    // The LLM occasionally returns an intent name with different casing/whitespace than the
    // catalog constant -- normalize before lookup rather than falling through to "unrecognized".
    private static String normalize(String intentName) {
        return intentName == null ? null : intentName.trim().toLowerCase();
    }

    public static Map<String, IntentSpec> all() {
        return INTENTS;
    }

    private static Map<String, IntentSpec> build() {
        Map<String, IntentSpec> map = new LinkedHashMap<>();

        map.put(FETCH_ORDER, new IntentSpec(FETCH_ORDER,
                "Retrieves a specific order by its identifier.",
                List.of(new EntitySpec("orderId", "string",
                        "The internal Shopify order ID or customer-facing order name (e.g. #1042).", true))));

        map.put(FETCH_LAST_10_ORDERS, new IntentSpec(FETCH_LAST_10_ORDERS,
                "Retrieves the ten most recent orders, optionally filtered by customer.",
                List.of(
                        // Spec marks this "Optional" -- never block slot-filling on it.
                        new EntitySpec("customerId", "string",
                                "Optional customer ID to scope the latest orders to a specific buyer.", false),
                        // Spec says "Fixed to 10" -- a constant, not something to ask the customer for.
                        new EntitySpec("limit", "integer", "Fixed to 10 for this intent.", false, 10))));

        map.put(FETCH_ORDERS_IN_DURATION, new IntentSpec(FETCH_ORDERS_IN_DURATION,
                "Retrieves orders created within a specific time range.",
                List.of(
                        new EntitySpec("start", "string (ISO 8601 date/timestamp)",
                                "The beginning of the time range.", true),
                        new EntitySpec("end", "string (ISO 8601 date/timestamp)",
                                "The end of the time range.", true))));

        map.put(CREATE_ORDER, new IntentSpec(CREATE_ORDER,
                "Creates a new order in Shopify.",
                List.of(
                        new EntitySpec("lineItems", "array of objects",
                                "List of products, variants, and quantities to include in the order.", true),
                        new EntitySpec("customerId", "string", "The customer ID placing the order.", true),
                        new EntitySpec("shippingAddress", "object",
                                "Destination address details for fulfillment.", true),
                        // A customer would never state this themselves in conversation -- default it like a
                        // real draft order would (pending payment) instead of interrogating them for it.
                        new EntitySpec("financialStatus", "string",
                                "Initial payment status (e.g. pending, paid).", false, "pending"))));

        map.put(CANCEL_ORDER, new IntentSpec(CANCEL_ORDER,
                "Cancels an existing active order.",
                List.of(
                        new EntitySpec("orderId", "string",
                                "The internal Shopify order ID of the order to cancel.", true),
                        new EntitySpec("reason", "string",
                                "The reason for cancellation (e.g. customer, fraudulent, inventory, declined).", true),
                        // Sensible default: notify the customer unless told otherwise.
                        new EntitySpec("emailNotification", "boolean",
                                "Whether to send a cancellation notification email to the customer.", false, true))));

        return Map.copyOf(map);
    }
}
