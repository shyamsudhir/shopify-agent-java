package com.app.supportspoc.pipeline;

import com.app.supportspoc.dto.shopify.ShopifyCatalogDto.ProductDetail;
import com.app.supportspoc.dto.shopify.ShopifyCatalogDto.ProductSearchFilter;
import com.app.supportspoc.dto.shopify.ShopifyCatalogDto.ProductSummary;
import com.app.supportspoc.dto.shopify.ShopifyCatalogDto.RefundSummary;
import com.app.supportspoc.dto.shopify.ShopifyCatalogDto.ReturnEligibilityResult;
import com.app.supportspoc.dto.shopify.ShopifyCatalogDto.ReturnSummary;
import com.app.supportspoc.dto.shopify.ShopifyCatalogDto.ShopPolicy;
import com.app.supportspoc.dto.shopify.ShopifyCustomerContextDto.CustomerContext;
import com.app.supportspoc.dto.shopify.ShopifyOrderContextDto.OrderSupportContext;
import com.app.supportspoc.util.shopify.ShopifyCustomerContextService;
import com.app.supportspoc.util.shopify.ShopifyOrderContextService;
import com.app.supportspoc.util.shopify.ShopifyPolicyService;
import com.app.supportspoc.util.shopify.ShopifyProductService;
import com.app.supportspoc.util.shopify.ShopifyReturnRefundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Real implementation of {@link ToolInvoker} for the "shopify.*" tool names
 * used throughout src/main/resources/workflows/*.json.
 *
 * WIRED (calls the real GraphQL-backed services):
 *   shopify.get_order, shopify.get_order_status, shopify.get_order_details,
 *   shopify.get_order_line_items, shopify.get_order_fulfillments, shopify.get_tracking
 *     -> ShopifyOrderContextService.getOrderSupportContext (one call, multiple projections)
 *   shopify.get_return_policy, shopify.get_shipping_policy -> ShopifyPolicyService
 *   shopify.check_return_eligibility, shopify.get_return_status, shopify.get_refunds,
 *   shopify.get_returnable_items -> ShopifyReturnRefundService
 *   shopify.get_customer -> ShopifyCustomerContextService
 *   shopify.get_product, shopify.search_products -> ShopifyProductService
 *
 * NOT YET WIRED: the rest of the ~119-tool catalog (carts, discounts, subscriptions,
 * B2B, etc.). Those fail cleanly with a "tool not wired" error rather than inventing
 * Shopify data -- WorkflowEngine turns that into a FAILED/BLOCKED task, same as any
 * other tool error, so the customer-facing response says "I couldn't check that"
 * instead of guessing. Extending coverage is a mechanical follow-up: add a case
 * below once the corresponding service method exists.
 *
 * Per-shop credentials: the workflow DSL never carries shop_domain/access_token
 * itself -- WorkflowEngine injects them into every tool call's params from
 * WorkflowExecutionContext's ambient input (see ToolRunnerServiceImpl), which today
 * falls back to the single-tenant app.shopify.* properties since real per-session
 * OAuth (ShopifyTokenExchangeService) isn't threaded through Session yet.
 */
@Component
public class ShopifyToolInvoker implements ToolInvoker {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyToolInvoker.class);

    private final ShopifyOrderContextService orderContextService;
    private final ShopifyPolicyService policyService;
    private final ShopifyReturnRefundService returnRefundService;
    private final ShopifyCustomerContextService customerContextService;
    private final ShopifyProductService productService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${shopify.mocked:false}")
    private boolean mocked;

    public ShopifyToolInvoker(ShopifyOrderContextService orderContextService,
                               ShopifyPolicyService policyService,
                               ShopifyReturnRefundService returnRefundService,
                               ShopifyCustomerContextService customerContextService,
                               ShopifyProductService productService) {
        this.orderContextService = orderContextService;
        this.policyService = policyService;
        this.returnRefundService = returnRefundService;
        this.customerContextService = customerContextService;
        this.productService = productService;
    }

    @Override
    public CompletableFuture<Map<String, Object>> invoke(String toolName, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (mocked) {
                    Map<String, Object> mock = mockResultFor(toolName, params);
                    if (mock != null) return mock;
                    // fall through to the real dispatch below only if no mock is defined;
                    // real dispatch will then (correctly) fail on missing credentials.
                }
                return dispatch(toolName, params);
            } catch (Exception e) {
                logger.warn("shopify_tool_failed - tool={}, params={}, error={}", toolName, params, e.getMessage());
                throw new ToolInvocationException(toolName, e);
            }
        });
    }

    private Map<String, Object> dispatch(String toolName, Map<String, Object> params) throws Exception {
        String shopDomain = stringParam(params, "shop_domain");
        String accessToken = stringParam(params, "access_token");
        if (toolName.startsWith("shopify.") && (shopDomain == null || accessToken == null)) {
            throw new IllegalStateException(
                    "no Shopify credentials available for this session yet (per-session OAuth via "
                            + "ShopifyTokenExchangeService isn't wired into Session) -- set app.shopify.shop-domain/"
                            + "app.shopify.access-token for single-tenant/dev use, or enable shopify.mocked=true");
        }

        return switch (toolName) {
            case "shopify.get_order", "shopify.get_order_context" -> toMap(fetchOrderContext(shopDomain, accessToken, params));

            case "shopify.get_order_status" -> {
                OrderSupportContext ctx = fetchOrderContext(shopDomain, accessToken, params);
                yield ctx == null ? Map.of() : Map.of(
                        "order_id", nullSafe(ctx.getId()),
                        "financial_status", nullSafe(ctx.getDisplayFinancialStatus()),
                        "fulfillment_status", nullSafe(ctx.getDisplayFulfillmentStatus()),
                        "closed", Boolean.TRUE.equals(ctx.getClosed()));
            }

            case "shopify.get_order_details" -> toMap(fetchOrderContext(shopDomain, accessToken, params));

            case "shopify.get_order_line_items" -> {
                OrderSupportContext ctx = fetchOrderContext(shopDomain, accessToken, params);
                yield Map.of("items", ctx == null ? List.of() : toMap(ctx).getOrDefault("lineItems", List.of()));
            }

            case "shopify.get_order_fulfillments" -> {
                OrderSupportContext ctx = fetchOrderContext(shopDomain, accessToken, params);
                yield Map.of("fulfillments", ctx == null ? List.of() : toMap(ctx).getOrDefault("fulfillments", List.of()));
            }

            case "shopify.get_tracking" -> {
                OrderSupportContext ctx = fetchOrderContext(shopDomain, accessToken, params);
                yield Map.of("fulfillments", ctx == null ? List.of() : toMap(ctx).getOrDefault("fulfillments", List.of()));
            }

            case "shopify.get_return_policy" -> {
                Optional<ShopPolicy> policy = policyService.getReturnPolicy(shopDomain, accessToken);
                yield policy.<Map<String, Object>>map(this::toMap).orElseGet(Map::of);
            }

            case "shopify.get_shipping_policy" -> {
                Optional<ShopPolicy> policy = policyService.getShippingPolicy(shopDomain, accessToken);
                yield policy.<Map<String, Object>>map(this::toMap).orElseGet(Map::of);
            }

            case "shopify.check_return_eligibility" -> {
                ReturnEligibilityResult result = returnRefundService.checkReturnEligibility(
                        shopDomain, accessToken, stringParam(params, "order_id"), stringParam(params, "customer_id"));
                yield toMap(result);
            }

            case "shopify.get_return_status" -> {
                List<ReturnSummary> statuses = returnRefundService.getReturnStatus(
                        shopDomain, accessToken, stringParam(params, "order_id"), stringParam(params, "customer_id"));
                yield Map.of("returns", toMap(statuses));
            }

            case "shopify.get_refunds" -> {
                List<RefundSummary> refunds = returnRefundService.getRefunds(
                        shopDomain, accessToken, stringParam(params, "order_id"), stringParam(params, "customer_id"));
                yield Map.of("refunds", toMap(refunds));
            }

            case "shopify.get_returnable_items" -> {
                var items = returnRefundService.getReturnableItems(shopDomain, accessToken, stringParam(params, "order_id"));
                yield Map.of("items", toMap(items));
            }

            case "shopify.get_customer" -> {
                CustomerContext customer = customerContextService.getCustomerContext(
                        shopDomain, accessToken, stringParam(params, "customer_id"));
                yield toMap(customer);
            }

            case "shopify.get_product" -> {
                ProductDetail detail = stringParam(params, "product_id") != null
                        ? productService.getProductDetails(shopDomain, accessToken, stringParam(params, "product_id"))
                        : productService.getProductDetailsByHandle(shopDomain, accessToken, stringParam(params, "handle"));
                yield toMap(detail);
            }

            case "shopify.search_products" -> {
                ProductSearchFilter filter = new ProductSearchFilter();
                filter.setKeyword(stringParam(params, "keyword") != null
                        ? stringParam(params, "keyword") : stringParam(params, "product_reference"));
                filter.setLimit(5);
                List<ProductSummary> results = productService.searchProducts(shopDomain, accessToken, filter);
                yield Map.of("products", toMap(results));
            }

            default -> throw new IllegalStateException("tool '" + toolName + "' is not yet wired to a backend implementation");
        };
    }

    private OrderSupportContext fetchOrderContext(String shopDomain, String accessToken, Map<String, Object> params) throws Exception {
        String orderRef = firstNonBlank(stringParam(params, "order_id"), stringParam(params, "order_name"), stringParam(params, "order_reference"));
        if (orderRef == null) {
            throw new IllegalArgumentException("get_order requires an order_id/order_reference");
        }
        return orderContextService.getOrderSupportContext(shopDomain, accessToken, orderRef, stringParam(params, "customer_id"));
    }

    /** Deterministic, clearly-labelled mock data for local/dev use (shopify.mocked=true). Returns null if no mock is defined for this tool. */
    private Map<String, Object> mockResultFor(String toolName, Map<String, Object> params) {
        String orderRef = firstNonBlank(stringParam(params, "order_id"), stringParam(params, "order_name"), stringParam(params, "order_reference"));
        return switch (toolName) {
            case "shopify.get_order", "shopify.get_order_context", "shopify.get_order_details" -> Map.of(
                    "_mock", true, "id", "gid://shopify/Order/mock-" + orderRef, "name", "#" + orderRef,
                    "displayFulfillmentStatus", "FULFILLED", "displayFinancialStatus", "PAID");
            case "shopify.get_order_status" -> Map.of(
                    "_mock", true, "order_id", orderRef, "fulfillment_status", "FULFILLED", "financial_status", "PAID");
            case "shopify.get_tracking" -> Map.of("_mock", true, "fulfillments", List.of(
                    Map.of("trackingInfo", List.of(Map.of("company", "UPS", "number", "1Z-MOCK", "url", "https://example.com/track")))));
            case "shopify.get_return_policy" -> Map.of("_mock", true, "windowDays", 30, "body", "Mock return policy: 30 days.");
            case "shopify.check_return_eligibility" -> Map.of("_mock", true, "eligible", true, "deadline", "2026-10-05");
            default -> null;
        };
    }

    private Map<String, Object> toMap(Object value) {
        if (value == null) return Map.of();
        return objectMapper.convertValue(value, Map.class);
    }

    private List<Object> toMap(List<?> values) {
        if (values == null) return List.of();
        return values.stream().map(v -> (Object) toMap(v)).toList();
    }

    private static String stringParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static Object nullSafe(Object value) {
        return value != null ? value : "";
    }

    public static final class ToolInvocationException extends RuntimeException {
        public ToolInvocationException(String toolName, Throwable cause) {
            super("tool '" + toolName + "' failed: " + cause.getMessage(), cause);
        }
    }
}
