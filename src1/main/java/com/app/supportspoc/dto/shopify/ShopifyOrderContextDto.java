package com.app.supportspoc.dto.shopify;

import java.util.List;

/**
 * ORDER_SUPPORT_CONTEXT and, as subsets of the same fetched object rather than separate
 * queries, ORDER_FULFILLMENT_CONTEXT / ORDER_RETURN_CONTEXT / ORDER_REFUND_CONTEXT:
 * everything a support agent needs about one order in a single GraphQL request.
 *
 * Superset of ShopifyOrderDto.OrderDetail -- adds billingAddress, transactions, refunds,
 * returns, and product/variant references on line items. Kept as its own type rather than
 * bolting these fields onto OrderDetail so the lighter get_order/status/tracking calls
 * (which don't need transaction/refund/return history) keep their smaller query and
 * response size.
 */
public class ShopifyOrderContextDto {

    public static class LineItemContext {
        private String id;
        private String title;
        private Integer quantity;
        private Integer currentQuantity;
        private String sku;
        private ShopifyOrderDto.MoneyBag originalUnitPriceSet;
        private String productId;
        private String productTitle;
        private String variantId;
        private String variantTitle;

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
        public ShopifyOrderDto.MoneyBag getOriginalUnitPriceSet() { return originalUnitPriceSet; }
        public void setOriginalUnitPriceSet(ShopifyOrderDto.MoneyBag originalUnitPriceSet) { this.originalUnitPriceSet = originalUnitPriceSet; }
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public String getProductTitle() { return productTitle; }
        public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
        public String getVariantId() { return variantId; }
        public void setVariantId(String variantId) { this.variantId = variantId; }
        public String getVariantTitle() { return variantTitle; }
        public void setVariantTitle(String variantTitle) { this.variantTitle = variantTitle; }
    }

    public static class TransactionContext {
        private String id;
        private String kind;   // SALE, CAPTURE, REFUND, VOID, AUTHORIZATION, ...
        private String status; // SUCCESS, PENDING, FAILURE, ERROR
        private String gateway;
        private String createdAt;
        private ShopifyOrderDto.MoneyBag amountSet;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getGateway() { return gateway; }
        public void setGateway(String gateway) { this.gateway = gateway; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public ShopifyOrderDto.MoneyBag getAmountSet() { return amountSet; }
        public void setAmountSet(ShopifyOrderDto.MoneyBag amountSet) { this.amountSet = amountSet; }
    }

    public static class OrderSupportContext {
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
        private ShopifyOrderDto.CustomerRef customer;
        private ShopifyOrderDto.MailingAddress shippingAddress;
        private ShopifyOrderDto.MailingAddress billingAddress;
        private ShopifyOrderDto.MoneyBag totalPriceSet;
        private List<LineItemContext> lineItems;
        private List<ShopifyOrderDto.Fulfillment> fulfillments;
        private List<TransactionContext> transactions;
        private List<ShopifyCatalogDto.RefundSummary> refunds;
        private List<ShopifyCatalogDto.ReturnSummary> returns;

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
        public ShopifyOrderDto.CustomerRef getCustomer() { return customer; }
        public void setCustomer(ShopifyOrderDto.CustomerRef customer) { this.customer = customer; }
        public ShopifyOrderDto.MailingAddress getShippingAddress() { return shippingAddress; }
        public void setShippingAddress(ShopifyOrderDto.MailingAddress shippingAddress) { this.shippingAddress = shippingAddress; }
        public ShopifyOrderDto.MailingAddress getBillingAddress() { return billingAddress; }
        public void setBillingAddress(ShopifyOrderDto.MailingAddress billingAddress) { this.billingAddress = billingAddress; }
        public ShopifyOrderDto.MoneyBag getTotalPriceSet() { return totalPriceSet; }
        public void setTotalPriceSet(ShopifyOrderDto.MoneyBag totalPriceSet) { this.totalPriceSet = totalPriceSet; }
        public List<LineItemContext> getLineItems() { return lineItems; }
        public void setLineItems(List<LineItemContext> lineItems) { this.lineItems = lineItems; }
        public List<ShopifyOrderDto.Fulfillment> getFulfillments() { return fulfillments; }
        public void setFulfillments(List<ShopifyOrderDto.Fulfillment> fulfillments) { this.fulfillments = fulfillments; }
        public List<TransactionContext> getTransactions() { return transactions; }
        public void setTransactions(List<TransactionContext> transactions) { this.transactions = transactions; }
        public List<ShopifyCatalogDto.RefundSummary> getRefunds() { return refunds; }
        public void setRefunds(List<ShopifyCatalogDto.RefundSummary> refunds) { this.refunds = refunds; }
        public List<ShopifyCatalogDto.ReturnSummary> getReturns() { return returns; }
        public void setReturns(List<ShopifyCatalogDto.ReturnSummary> returns) { this.returns = returns; }
    }
}
