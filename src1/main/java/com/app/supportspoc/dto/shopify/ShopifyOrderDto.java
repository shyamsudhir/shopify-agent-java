package com.app.supportspoc.dto.shopify;

import java.util.List;
import java.util.Map;

/**
 * DTOs supporting the Order Management use cases: get_order, get_order_status,
 * get_fulfillment, get_tracking, get_delivery_estimate, search_orders,
 * cancel_order, check_cancel_eligibility.
 *
 * Kept as a separate holder class (mirrors the ShopifyDto.* nested-static pattern)
 * so it can be merged into ShopifyDto.java or left standalone with minimal changes.
 */
public class ShopifyOrderDto {

    // ---------- Shared value types ----------

    public static class Money {
        private String amount;
        private String currencyCode;

        public String getAmount() { return amount; }
        public void setAmount(String amount) { this.amount = amount; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    }

    public static class MoneyBag {
        private Money shopMoney;

        public Money getShopMoney() { return shopMoney; }
        public void setShopMoney(Money shopMoney) { this.shopMoney = shopMoney; }
    }

    public static class CustomerRef {
        private String id;
        private String displayName;
        private String email;
        private String phone;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class MailingAddress {
        private String address1;
        private String address2;
        private String city;
        private String province;
        private String zip;
        private String countryCodeV2;

        public String getAddress1() { return address1; }
        public void setAddress1(String address1) { this.address1 = address1; }
        public String getAddress2() { return address2; }
        public void setAddress2(String address2) { this.address2 = address2; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getProvince() { return province; }
        public void setProvince(String province) { this.province = province; }
        public String getZip() { return zip; }
        public void setZip(String zip) { this.zip = zip; }
        public String getCountryCodeV2() { return countryCodeV2; }
        public void setCountryCodeV2(String countryCodeV2) { this.countryCodeV2 = countryCodeV2; }
    }

    public static class LineItem {
        private String id;
        private String title;
        private Integer quantity;
        private Integer currentQuantity;
        private String sku;
        private MoneyBag originalUnitPriceSet;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Integer getCurrentQuantity() { return currentQuantity; }
        public void setCurrentQuantity(Integer currentQuantity) { this.currentQuantity = currentQuantity; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public MoneyBag getOriginalUnitPriceSet() { return originalUnitPriceSet; }
        public void setOriginalUnitPriceSet(MoneyBag originalUnitPriceSet) { this.originalUnitPriceSet = originalUnitPriceSet; }
    }

    public static class LineItemConnection {
        private List<LineItem> nodes;
        public List<LineItem> getNodes() { return nodes; }
        public void setNodes(List<LineItem> nodes) { this.nodes = nodes; }
    }

    public static class TrackingInfo {
        private String company;
        private String number;
        private String url;

        public String getCompany() { return company; }
        public void setCompany(String company) { this.company = company; }
        public String getNumber() { return number; }
        public void setNumber(String number) { this.number = number; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public static class Fulfillment {
        private String id;
        private String status;              // FulfillmentStatus: pending, open, success, cancelled, error, failure
        private String displayStatus;       // FulfillmentDisplayStatus: IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, ...
        private String createdAt;
        private String updatedAt;
        private String estimatedDeliveryAt;
        private String deliveredAt;
        private String inTransitAt;
        private List<TrackingInfo> trackingInfo;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getDisplayStatus() { return displayStatus; }
        public void setDisplayStatus(String displayStatus) { this.displayStatus = displayStatus; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
        public String getEstimatedDeliveryAt() { return estimatedDeliveryAt; }
        public void setEstimatedDeliveryAt(String estimatedDeliveryAt) { this.estimatedDeliveryAt = estimatedDeliveryAt; }
        public String getDeliveredAt() { return deliveredAt; }
        public void setDeliveredAt(String deliveredAt) { this.deliveredAt = deliveredAt; }
        public String getInTransitAt() { return inTransitAt; }
        public void setInTransitAt(String inTransitAt) { this.inTransitAt = inTransitAt; }
        public List<TrackingInfo> getTrackingInfo() { return trackingInfo; }
        public void setTrackingInfo(List<TrackingInfo> trackingInfo) { this.trackingInfo = trackingInfo; }
    }

    // ---------- Order detail (get_order / status / fulfillment / tracking / delivery estimate) ----------

    public static class OrderDetail {
        private String id;
        private String name;
        private String createdAt;
        private String displayFinancialStatus;
        private String displayFulfillmentStatus;
        private String cancelledAt;
        private String cancelReason;
        private Boolean closed;
        private Boolean fulfillable;
        private Boolean restockable;
        private Boolean refundable;
        private String statusPageUrl;
        private CustomerRef customer;
        private MailingAddress shippingAddress;
        private MoneyBag totalPriceSet;
        private LineItemConnection lineItems;
        private List<Fulfillment> fulfillments;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getDisplayFinancialStatus() { return displayFinancialStatus; }
        public void setDisplayFinancialStatus(String displayFinancialStatus) { this.displayFinancialStatus = displayFinancialStatus; }
        public String getDisplayFulfillmentStatus() { return displayFulfillmentStatus; }
        public void setDisplayFulfillmentStatus(String displayFulfillmentStatus) { this.displayFulfillmentStatus = displayFulfillmentStatus; }
        public String getCancelledAt() { return cancelledAt; }
        public void setCancelledAt(String cancelledAt) { this.cancelledAt = cancelledAt; }
        public String getCancelReason() { return cancelReason; }
        public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
        public Boolean getClosed() { return closed; }
        public void setClosed(Boolean closed) { this.closed = closed; }
        public Boolean getFulfillable() { return fulfillable; }
        public void setFulfillable(Boolean fulfillable) { this.fulfillable = fulfillable; }
        public Boolean getRestockable() { return restockable; }
        public void setRestockable(Boolean restockable) { this.restockable = restockable; }
        public Boolean getRefundable() { return refundable; }
        public void setRefundable(Boolean refundable) { this.refundable = refundable; }
        public String getStatusPageUrl() { return statusPageUrl; }
        public void setStatusPageUrl(String statusPageUrl) { this.statusPageUrl = statusPageUrl; }
        public CustomerRef getCustomer() { return customer; }
        public void setCustomer(CustomerRef customer) { this.customer = customer; }
        public MailingAddress getShippingAddress() { return shippingAddress; }
        public void setShippingAddress(MailingAddress shippingAddress) { this.shippingAddress = shippingAddress; }
        public MoneyBag getTotalPriceSet() { return totalPriceSet; }
        public void setTotalPriceSet(MoneyBag totalPriceSet) { this.totalPriceSet = totalPriceSet; }
        public LineItemConnection getLineItems() { return lineItems; }
        public void setLineItems(LineItemConnection lineItems) { this.lineItems = lineItems; }
        public List<Fulfillment> getFulfillments() { return fulfillments; }
        public void setFulfillments(List<Fulfillment> fulfillments) { this.fulfillments = fulfillments; }
    }

    public static class OrderDetailEdge {
        private OrderDetail node;
        public OrderDetail getNode() { return node; }
        public void setNode(OrderDetail node) { this.node = node; }
    }

    public static class OrderDetailConnection {
        private List<OrderDetailEdge> edges;
        public List<OrderDetailEdge> getEdges() { return edges; }
        public void setEdges(List<OrderDetailEdge> edges) { this.edges = edges; }
    }

    public static class OrderDetailData {
        private OrderDetailConnection orders;
        public OrderDetailConnection getOrders() { return orders; }
        public void setOrders(OrderDetailConnection orders) { this.orders = orders; }
    }

    // ---------- Order summary (search_orders) ----------

    public static class OrderSummary {
        private String id;
        private String name;
        private String createdAt;
        private String displayFinancialStatus;
        private String displayFulfillmentStatus;
        private Boolean closed;
        private MoneyBag totalPriceSet;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getDisplayFinancialStatus() { return displayFinancialStatus; }
        public void setDisplayFinancialStatus(String displayFinancialStatus) { this.displayFinancialStatus = displayFinancialStatus; }
        public String getDisplayFulfillmentStatus() { return displayFulfillmentStatus; }
        public void setDisplayFulfillmentStatus(String displayFulfillmentStatus) { this.displayFulfillmentStatus = displayFulfillmentStatus; }
        public Boolean getClosed() { return closed; }
        public void setClosed(Boolean closed) { this.closed = closed; }
        public MoneyBag getTotalPriceSet() { return totalPriceSet; }
        public void setTotalPriceSet(MoneyBag totalPriceSet) { this.totalPriceSet = totalPriceSet; }
    }

    public static class OrderSummaryEdge {
        private OrderSummary node;
        public OrderSummary getNode() { return node; }
        public void setNode(OrderSummary node) { this.node = node; }
    }

    public static class OrderSummaryConnection {
        private List<OrderSummaryEdge> edges;
        public List<OrderSummaryEdge> getEdges() { return edges; }
        public void setEdges(List<OrderSummaryEdge> edges) { this.edges = edges; }
    }

    public static class OrderSummaryData {
        private OrderSummaryConnection orders;
        public OrderSummaryConnection getOrders() { return orders; }
        public void setOrders(OrderSummaryConnection orders) { this.orders = orders; }
    }

    // ---------- search_orders request ----------

    /**
     * Tool-facing filter for search_orders. customerId is mandatory: this service
     * is called on behalf of an authenticated customer and must never return
     * another customer's orders.
     */
    public static class SearchOrdersFilter {
        private String customerId;          // required, numeric Shopify customer id (not gid)
        private String fulfillmentStatus;   // e.g. "unfulfilled", "fulfilled", "partial", "scheduled", "on_hold"
        private String financialStatus;     // e.g. "paid", "pending", "refunded"
        private String createdAfter;        // ISO-8601, e.g. "2026-07-01"
        private String createdBefore;       // ISO-8601, e.g. "2026-07-31"
        private Integer limit;              // default 20, capped at 50 in the service

        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getFulfillmentStatus() { return fulfillmentStatus; }
        public void setFulfillmentStatus(String fulfillmentStatus) { this.fulfillmentStatus = fulfillmentStatus; }
        public String getFinancialStatus() { return financialStatus; }
        public void setFinancialStatus(String financialStatus) { this.financialStatus = financialStatus; }
        public String getCreatedAfter() { return createdAfter; }
        public void setCreatedAfter(String createdAfter) { this.createdAfter = createdAfter; }
        public String getCreatedBefore() { return createdBefore; }
        public void setCreatedBefore(String createdBefore) { this.createdBefore = createdBefore; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
    }

    // ---------- check_cancel_eligibility result ----------

    public static class CancelEligibilityResult {
        private boolean eligible;
        private List<String> reasons;       // human-readable reasons when not eligible (or notes when eligible)
        private OrderDetail order;          // raw snapshot the decision was based on, for the agent/business layer

        public boolean isEligible() { return eligible; }
        public void setEligible(boolean eligible) { this.eligible = eligible; }
        public List<String> getReasons() { return reasons; }
        public void setReasons(List<String> reasons) { this.reasons = reasons; }
        public OrderDetail getOrder() { return order; }
        public void setOrder(OrderDetail order) { this.order = order; }
    }

    // ---------- cancel_order request/result ----------

    public static class CancelOrderRequest {
        private String orderId;                 // gid://shopify/Order/... -- resolve via getOrderDetails first
        private String reason;                   // OrderCancelReason: CUSTOMER, DECLINED, FRAUD, INVENTORY, OTHER, STAFF
        private boolean restock;
        private boolean notifyCustomer;
        private String staffNote;                // merchant-facing only, never shown to the customer
        /**
         * Raw GraphQL input for OrderCancelRefundMethodInput, e.g. {"originalPaymentMethodsRefund": {}}.
         * Left as a raw map rather than a typed DTO: verify the exact sub-fields against your store's
         * schema (see Important assumptions) before hardcoding this shape.
         */
        private Map<String, Object> refundMethod;

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public boolean isRestock() { return restock; }
        public void setRestock(boolean restock) { this.restock = restock; }
        public boolean isNotifyCustomer() { return notifyCustomer; }
        public void setNotifyCustomer(boolean notifyCustomer) { this.notifyCustomer = notifyCustomer; }
        public String getStaffNote() { return staffNote; }
        public void setStaffNote(String staffNote) { this.staffNote = staffNote; }
        public Map<String, Object> getRefundMethod() { return refundMethod; }
        public void setRefundMethod(Map<String, Object> refundMethod) { this.refundMethod = refundMethod; }
    }

    public static class UserError {
        private List<String> field;
        private String message;
        private String code;

        public List<String> getField() { return field; }
        public void setField(List<String> field) { this.field = field; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public static class OrderCancelJob {
        private String id;
        private boolean done;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public boolean isDone() { return done; }
        public void setDone(boolean done) { this.done = done; }
    }

    public static class OrderCancelPayload {
        private OrderCancelJob job;
        private List<UserError> orderCancelUserErrors;

        public OrderCancelJob getJob() { return job; }
        public void setJob(OrderCancelJob job) { this.job = job; }
        public List<UserError> getOrderCancelUserErrors() { return orderCancelUserErrors; }
        public void setOrderCancelUserErrors(List<UserError> orderCancelUserErrors) { this.orderCancelUserErrors = orderCancelUserErrors; }
    }

    public static class OrderCancelData {
        private OrderCancelPayload orderCancel;
        public OrderCancelPayload getOrderCancel() { return orderCancel; }
        public void setOrderCancel(OrderCancelPayload orderCancel) { this.orderCancel = orderCancel; }
    }

    /** What cancel_order hands back to the agent layer: async job status, not a final confirmation. */
    public static class CancelOrderResult {
        private boolean submitted;
        private String jobId;
        private boolean jobDone;
        private List<String> errors;

        public boolean isSubmitted() { return submitted; }
        public void setSubmitted(boolean submitted) { this.submitted = submitted; }
        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        public boolean isJobDone() { return jobDone; }
        public void setJobDone(boolean jobDone) { this.jobDone = jobDone; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }
}
