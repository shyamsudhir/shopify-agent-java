package com.app.supportspoc.dto.shopify;

import java.util.List;

public class ShopifyOrderContextResponseDto {

    public static class ProductRef { private String id; private String title; public String getId() { return id; } public void setId(String id) { this.id = id; } public String getTitle() { return title; } public void setTitle(String title) { this.title = title; } }
    public static class VariantRef { private String id; private String title; public String getId() { return id; } public void setId(String id) { this.id = id; } public String getTitle() { return title; } public void setTitle(String title) { this.title = title; } }

    public static class LineItemContextNode {
        private String id, title, sku;
        private Integer quantity, currentQuantity;
        private ShopifyOrderDto.MoneyBag originalUnitPriceSet;
        private ProductRef product;
        private VariantRef variant;

        public String getId() { return id; } public void setId(String id) { this.id = id; }
        public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
        public String getSku() { return sku; } public void setSku(String sku) { this.sku = sku; }
        public Integer getQuantity() { return quantity; } public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Integer getCurrentQuantity() { return currentQuantity; } public void setCurrentQuantity(Integer currentQuantity) { this.currentQuantity = currentQuantity; }
        public ShopifyOrderDto.MoneyBag getOriginalUnitPriceSet() { return originalUnitPriceSet; } public void setOriginalUnitPriceSet(ShopifyOrderDto.MoneyBag v) { this.originalUnitPriceSet = v; }
        public ProductRef getProduct() { return product; } public void setProduct(ProductRef product) { this.product = product; }
        public VariantRef getVariant() { return variant; } public void setVariant(VariantRef variant) { this.variant = variant; }
    }

    public static class LineItemContextConnection { private List<LineItemContextNode> nodes; public List<LineItemContextNode> getNodes() { return nodes; } public void setNodes(List<LineItemContextNode> nodes) { this.nodes = nodes; } }

    public static class TransactionNode {
        private String id, kind, status, gateway, createdAt;
        private ShopifyOrderDto.MoneyBag amountSet;
        public String getId() { return id; } public void setId(String id) { this.id = id; }
        public String getKind() { return kind; } public void setKind(String kind) { this.kind = kind; }
        public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
        public String getGateway() { return gateway; } public void setGateway(String gateway) { this.gateway = gateway; }
        public String getCreatedAt() { return createdAt; } public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public ShopifyOrderDto.MoneyBag getAmountSet() { return amountSet; } public void setAmountSet(ShopifyOrderDto.MoneyBag v) { this.amountSet = v; }
    }

    public static class RefundNode {
        private String id, createdAt, processedAt, note;
        private ShopifyOrderDto.MoneyBag totalRefundedSet;
        public String getId() { return id; } public void setId(String id) { this.id = id; }
        public String getCreatedAt() { return createdAt; } public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getProcessedAt() { return processedAt; } public void setProcessedAt(String processedAt) { this.processedAt = processedAt; }
        public String getNote() { return note; } public void setNote(String note) { this.note = note; }
        public ShopifyOrderDto.MoneyBag getTotalRefundedSet() { return totalRefundedSet; } public void setTotalRefundedSet(ShopifyOrderDto.MoneyBag v) { this.totalRefundedSet = v; }
    }

    public static class ReturnNode {
        private String id, name, status, createdAt;
        private Integer totalQuantity;
        public String getId() { return id; } public void setId(String id) { this.id = id; }
        public String getName() { return name; } public void setName(String name) { this.name = name; }
        public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
        public String getCreatedAt() { return createdAt; } public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public Integer getTotalQuantity() { return totalQuantity; } public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }
    }
    public static class ReturnEdge { private ReturnNode node; public ReturnNode getNode() { return node; } public void setNode(ReturnNode node) { this.node = node; } }
    public static class ReturnConnection { private List<ReturnEdge> edges; public List<ReturnEdge> getEdges() { return edges; } public void setEdges(List<ReturnEdge> edges) { this.edges = edges; } }

    public static class OrderSupportContextNode {
        private String id, name, createdAt, displayFinancialStatus, displayFulfillmentStatus, cancelledAt, cancelReason, statusPageUrl;
        private Boolean closed, fulfillable, restockable, refundable;
        private ShopifyOrderDto.CustomerRef customer;
        private ShopifyOrderDto.MailingAddress shippingAddress;
        private ShopifyOrderDto.MailingAddress billingAddress;
        private ShopifyOrderDto.MoneyBag totalPriceSet;
        private LineItemContextConnection lineItems;
        private List<ShopifyOrderDto.Fulfillment> fulfillments;
        private List<TransactionNode> transactions;
        private List<RefundNode> refunds;
        private ReturnConnection returns;

        public String getId() { return id; } public void setId(String id) { this.id = id; }
        public String getName() { return name; } public void setName(String name) { this.name = name; }
        public String getCreatedAt() { return createdAt; } public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getDisplayFinancialStatus() { return displayFinancialStatus; } public void setDisplayFinancialStatus(String v) { this.displayFinancialStatus = v; }
        public String getDisplayFulfillmentStatus() { return displayFulfillmentStatus; } public void setDisplayFulfillmentStatus(String v) { this.displayFulfillmentStatus = v; }
        public String getCancelledAt() { return cancelledAt; } public void setCancelledAt(String cancelledAt) { this.cancelledAt = cancelledAt; }
        public String getCancelReason() { return cancelReason; } public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
        public String getStatusPageUrl() { return statusPageUrl; } public void setStatusPageUrl(String statusPageUrl) { this.statusPageUrl = statusPageUrl; }
        public Boolean getClosed() { return closed; } public void setClosed(Boolean closed) { this.closed = closed; }
        public Boolean getFulfillable() { return fulfillable; } public void setFulfillable(Boolean fulfillable) { this.fulfillable = fulfillable; }
        public Boolean getRestockable() { return restockable; } public void setRestockable(Boolean restockable) { this.restockable = restockable; }
        public Boolean getRefundable() { return refundable; } public void setRefundable(Boolean refundable) { this.refundable = refundable; }
        public ShopifyOrderDto.CustomerRef getCustomer() { return customer; } public void setCustomer(ShopifyOrderDto.CustomerRef customer) { this.customer = customer; }
        public ShopifyOrderDto.MailingAddress getShippingAddress() { return shippingAddress; } public void setShippingAddress(ShopifyOrderDto.MailingAddress v) { this.shippingAddress = v; }
        public ShopifyOrderDto.MailingAddress getBillingAddress() { return billingAddress; } public void setBillingAddress(ShopifyOrderDto.MailingAddress v) { this.billingAddress = v; }
        public ShopifyOrderDto.MoneyBag getTotalPriceSet() { return totalPriceSet; } public void setTotalPriceSet(ShopifyOrderDto.MoneyBag v) { this.totalPriceSet = v; }
        public LineItemContextConnection getLineItems() { return lineItems; } public void setLineItems(LineItemContextConnection lineItems) { this.lineItems = lineItems; }
        public List<ShopifyOrderDto.Fulfillment> getFulfillments() { return fulfillments; } public void setFulfillments(List<ShopifyOrderDto.Fulfillment> fulfillments) { this.fulfillments = fulfillments; }
        public List<TransactionNode> getTransactions() { return transactions; } public void setTransactions(List<TransactionNode> transactions) { this.transactions = transactions; }
        public List<RefundNode> getRefunds() { return refunds; } public void setRefunds(List<RefundNode> refunds) { this.refunds = refunds; }
        public ReturnConnection getReturns() { return returns; } public void setReturns(ReturnConnection returns) { this.returns = returns; }
    }

    public static class OrderSupportContextEdge { private OrderSupportContextNode node; public OrderSupportContextNode getNode() { return node; } public void setNode(OrderSupportContextNode node) { this.node = node; } }
    public static class OrderSupportContextConnection { private List<OrderSupportContextEdge> edges; public List<OrderSupportContextEdge> getEdges() { return edges; } public void setEdges(List<OrderSupportContextEdge> edges) { this.edges = edges; } }
    public static class OrderSupportContextData { private OrderSupportContextConnection orders; public OrderSupportContextConnection getOrders() { return orders; } public void setOrders(OrderSupportContextConnection orders) { this.orders = orders; } }
}
