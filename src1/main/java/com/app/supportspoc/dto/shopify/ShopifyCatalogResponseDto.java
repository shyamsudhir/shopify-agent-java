package com.app.supportspoc.dto.shopify;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Raw GraphQL response shapes (edges/nodes/connections) for the returns, refunds,
 * product, and shop-policy queries. Kept separate from ShopifyCatalogDto so the
 * tool-facing DTOs there stay clean; the service maps between the two.
 */
public class ShopifyCatalogResponseDto {

    // ---------- returnableFulfillments ----------

    public static class ReturnableFulfillmentLineItemNode {
        private FulfillmentLineItemRef fulfillmentLineItem;
        private Integer quantity;
        public FulfillmentLineItemRef getFulfillmentLineItem() { return fulfillmentLineItem; }
        public void setFulfillmentLineItem(FulfillmentLineItemRef fulfillmentLineItem) { this.fulfillmentLineItem = fulfillmentLineItem; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    public static class FulfillmentLineItemRef {
        private String id;
        private LineItemRef lineItem;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public LineItemRef getLineItem() { return lineItem; }
        public void setLineItem(LineItemRef lineItem) { this.lineItem = lineItem; }
    }

    public static class LineItemRef {
        private String id;
        private String title;
        private String sku;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
    }

    public static class ReturnableFulfillmentLineItemEdge {
        private ReturnableFulfillmentLineItemNode node;
        public ReturnableFulfillmentLineItemNode getNode() { return node; }
        public void setNode(ReturnableFulfillmentLineItemNode node) { this.node = node; }
    }

    public static class ReturnableFulfillmentLineItemConnection {
        private List<ReturnableFulfillmentLineItemEdge> edges;
        public List<ReturnableFulfillmentLineItemEdge> getEdges() { return edges; }
        public void setEdges(List<ReturnableFulfillmentLineItemEdge> edges) { this.edges = edges; }
    }

    public static class ReturnableFulfillmentNode {
        private String id;
        private ReturnableFulfillmentLineItemConnection returnableFulfillmentLineItems;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public ReturnableFulfillmentLineItemConnection getReturnableFulfillmentLineItems() { return returnableFulfillmentLineItems; }
        public void setReturnableFulfillmentLineItems(ReturnableFulfillmentLineItemConnection returnableFulfillmentLineItems) { this.returnableFulfillmentLineItems = returnableFulfillmentLineItems; }
    }

    public static class ReturnableFulfillmentEdge {
        private ReturnableFulfillmentNode node;
        public ReturnableFulfillmentNode getNode() { return node; }
        public void setNode(ReturnableFulfillmentNode node) { this.node = node; }
    }

    public static class ReturnableFulfillmentConnection {
        private List<ReturnableFulfillmentEdge> edges;
        public List<ReturnableFulfillmentEdge> getEdges() { return edges; }
        public void setEdges(List<ReturnableFulfillmentEdge> edges) { this.edges = edges; }
    }

    public static class ReturnableFulfillmentsData {
        private ReturnableFulfillmentConnection returnableFulfillments;
        public ReturnableFulfillmentConnection getReturnableFulfillments() { return returnableFulfillments; }
        public void setReturnableFulfillments(ReturnableFulfillmentConnection returnableFulfillments) { this.returnableFulfillments = returnableFulfillments; }
    }

    // ---------- returnRequest mutation ----------

    public static class ReturnRequestReturnNode {
        private String id;
        private String name;
        private String status;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class ReturnRequestPayload {
        @JsonProperty("return") // "return" is a Java reserved word, so the Java field/getter are renamed
        private ReturnRequestReturnNode returnRecord;
        private List<ShopifyOrderDto.UserError> userErrors;
        public ReturnRequestReturnNode getReturnRecord() { return returnRecord; }
        public void setReturnRecord(ReturnRequestReturnNode returnRecord) { this.returnRecord = returnRecord; }
        public List<ShopifyOrderDto.UserError> getUserErrors() { return userErrors; }
        public void setUserErrors(List<ShopifyOrderDto.UserError> userErrors) { this.userErrors = userErrors; }
    }

    public static class ReturnRequestData {
        private ReturnRequestPayload returnRequest;
        public ReturnRequestPayload getReturnRequest() { return returnRequest; }
        public void setReturnRequest(ReturnRequestPayload returnRequest) { this.returnRequest = returnRequest; }
    }

    // ---------- Order.returns (return_status) ----------

    public static class ReturnSummaryNode {
        private String id;
        private String name;
        private String status;
        private String createdAt;
        private Integer totalQuantity;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public Integer getTotalQuantity() { return totalQuantity; }
        public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }
    }

    public static class ReturnSummaryEdge {
        private ReturnSummaryNode node;
        public ReturnSummaryNode getNode() { return node; }
        public void setNode(ReturnSummaryNode node) { this.node = node; }
    }

    public static class ReturnSummaryConnection {
        private List<ReturnSummaryEdge> edges;
        public List<ReturnSummaryEdge> getEdges() { return edges; }
        public void setEdges(List<ReturnSummaryEdge> edges) { this.edges = edges; }
    }

    public static class OrderReturnsNode {
        private ReturnSummaryConnection returns;
        public ReturnSummaryConnection getReturns() { return returns; }
        public void setReturns(ReturnSummaryConnection returns) { this.returns = returns; }
    }

    public static class OrderReturnsEdge {
        private OrderReturnsNode node;
        public OrderReturnsNode getNode() { return node; }
        public void setNode(OrderReturnsNode node) { this.node = node; }
    }

    public static class OrderReturnsConnection {
        private List<OrderReturnsEdge> edges;
        public List<OrderReturnsEdge> getEdges() { return edges; }
        public void setEdges(List<OrderReturnsEdge> edges) { this.edges = edges; }
    }

    public static class OrderReturnsData {
        private OrderReturnsConnection orders;
        public OrderReturnsConnection getOrders() { return orders; }
        public void setOrders(OrderReturnsConnection orders) { this.orders = orders; }
    }

    // ---------- Order.refunds (refund_status / refund_details) ----------

    public static class OrderTransactionRef {
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class OrderTransactionConnectionRef {
        private List<OrderTransactionRef> nodes;
        public List<OrderTransactionRef> getNodes() { return nodes; }
        public void setNodes(List<OrderTransactionRef> nodes) { this.nodes = nodes; }
    }

    public static class RefundNode {
        private String id;
        private String createdAt;
        private String processedAt;
        private String note;
        private ShopifyOrderDto.MoneyBag totalRefundedSet;
        private OrderTransactionConnectionRef transactions;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getProcessedAt() { return processedAt; }
        public void setProcessedAt(String processedAt) { this.processedAt = processedAt; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public ShopifyOrderDto.MoneyBag getTotalRefundedSet() { return totalRefundedSet; }
        public void setTotalRefundedSet(ShopifyOrderDto.MoneyBag totalRefundedSet) { this.totalRefundedSet = totalRefundedSet; }
        public OrderTransactionConnectionRef getTransactions() { return transactions; }
        public void setTransactions(OrderTransactionConnectionRef transactions) { this.transactions = transactions; }
    }

    public static class OrderRefundsNode {
        private List<RefundNode> refunds;
        public List<RefundNode> getRefunds() { return refunds; }
        public void setRefunds(List<RefundNode> refunds) { this.refunds = refunds; }
    }

    public static class OrderRefundsEdge {
        private OrderRefundsNode node;
        public OrderRefundsNode getNode() { return node; }
        public void setNode(OrderRefundsNode node) { this.node = node; }
    }

    public static class OrderRefundsConnection {
        private List<OrderRefundsEdge> edges;
        public List<OrderRefundsEdge> getEdges() { return edges; }
        public void setEdges(List<OrderRefundsEdge> edges) { this.edges = edges; }
    }

    public static class OrderRefundsData {
        private OrderRefundsConnection orders;
        public OrderRefundsConnection getOrders() { return orders; }
        public void setOrders(OrderRefundsConnection orders) { this.orders = orders; }
    }

    // ---------- products (search) ----------

    public static class PriceRangeV2 {
        private ShopifyOrderDto.Money minVariantPrice;
        private ShopifyOrderDto.Money maxVariantPrice;
        public ShopifyOrderDto.Money getMinVariantPrice() { return minVariantPrice; }
        public void setMinVariantPrice(ShopifyOrderDto.Money minVariantPrice) { this.minVariantPrice = minVariantPrice; }
        public ShopifyOrderDto.Money getMaxVariantPrice() { return maxVariantPrice; }
        public void setMaxVariantPrice(ShopifyOrderDto.Money maxVariantPrice) { this.maxVariantPrice = maxVariantPrice; }
    }

    public static class ImageRef {
        private String url;
        private String altText;
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getAltText() { return altText; }
        public void setAltText(String altText) { this.altText = altText; }
    }

    public static class ProductSearchNode {
        private String id;
        private String title;
        private String productType;
        private String status;
        private Integer totalInventory;
        private PriceRangeV2 priceRangeV2;
        private ImageRef featuredImage;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getTotalInventory() { return totalInventory; }
        public void setTotalInventory(Integer totalInventory) { this.totalInventory = totalInventory; }
        public PriceRangeV2 getPriceRangeV2() { return priceRangeV2; }
        public void setPriceRangeV2(PriceRangeV2 priceRangeV2) { this.priceRangeV2 = priceRangeV2; }
        public ImageRef getFeaturedImage() { return featuredImage; }
        public void setFeaturedImage(ImageRef featuredImage) { this.featuredImage = featuredImage; }
    }

    public static class ProductSearchEdge {
        private ProductSearchNode node;
        public ProductSearchNode getNode() { return node; }
        public void setNode(ProductSearchNode node) { this.node = node; }
    }

    public static class ProductSearchConnection {
        private List<ProductSearchEdge> edges;
        public List<ProductSearchEdge> getEdges() { return edges; }
        public void setEdges(List<ProductSearchEdge> edges) { this.edges = edges; }
    }

    public static class ProductSearchData {
        private ProductSearchConnection products;
        public ProductSearchConnection getProducts() { return products; }
        public void setProducts(ProductSearchConnection products) { this.products = products; }
    }

    // ---------- product (details/variants/inventory/images) ----------

    public static class SelectedOptionRef {
        private String name;
        private String value;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public static class ProductVariantNode {
        private String id;
        private String title;
        private String sku;
        private String price;
        private Boolean availableForSale;
        private Integer inventoryQuantity;
        private List<SelectedOptionRef> selectedOptions;
        private ImageRef image;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getPrice() { return price; }
        public void setPrice(String price) { this.price = price; }
        public Boolean getAvailableForSale() { return availableForSale; }
        public void setAvailableForSale(Boolean availableForSale) { this.availableForSale = availableForSale; }
        public Integer getInventoryQuantity() { return inventoryQuantity; }
        public void setInventoryQuantity(Integer inventoryQuantity) { this.inventoryQuantity = inventoryQuantity; }
        public List<SelectedOptionRef> getSelectedOptions() { return selectedOptions; }
        public void setSelectedOptions(List<SelectedOptionRef> selectedOptions) { this.selectedOptions = selectedOptions; }
        public ImageRef getImage() { return image; }
        public void setImage(ImageRef image) { this.image = image; }
    }

    public static class ProductVariantConnection {
        private List<ProductVariantNode> nodes;
        public List<ProductVariantNode> getNodes() { return nodes; }
        public void setNodes(List<ProductVariantNode> nodes) { this.nodes = nodes; }
    }

    public static class ProductImageConnection {
        private List<ImageRef> nodes;
        public List<ImageRef> getNodes() { return nodes; }
        public void setNodes(List<ImageRef> nodes) { this.nodes = nodes; }
    }

    public static class ProductDetailNode {
        private String id;
        private String title;
        private String descriptionHtml;
        private String productType;
        private String vendor;
        private String status;
        private Integer totalInventory;
        private ProductImageConnection images;
        private ProductVariantConnection variants;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescriptionHtml() { return descriptionHtml; }
        public void setDescriptionHtml(String descriptionHtml) { this.descriptionHtml = descriptionHtml; }
        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }
        public String getVendor() { return vendor; }
        public void setVendor(String vendor) { this.vendor = vendor; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getTotalInventory() { return totalInventory; }
        public void setTotalInventory(Integer totalInventory) { this.totalInventory = totalInventory; }
        public ProductImageConnection getImages() { return images; }
        public void setImages(ProductImageConnection images) { this.images = images; }
        public ProductVariantConnection getVariants() { return variants; }
        public void setVariants(ProductVariantConnection variants) { this.variants = variants; }
    }

    public static class ProductDetailData {
        private ProductDetailNode product;
        public ProductDetailNode getProduct() { return product; }
        public void setProduct(ProductDetailNode product) { this.product = product; }
    }

    // ---------- shop { shopPolicies, billingAddress } ----------

    public static class ShopPolicyNode {
        private String type;
        private String title;
        private String body;
        private String url;
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public static class ShopAddressNode {
        private String address1;
        private String address2;
        private String city;
        private String province;
        private String zip;
        private String countryCodeV2;
        private String phone;

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
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class ShopNode {
        private List<ShopPolicyNode> shopPolicies;
        private ShopAddressNode billingAddress;
        public List<ShopPolicyNode> getShopPolicies() { return shopPolicies; }
        public void setShopPolicies(List<ShopPolicyNode> shopPolicies) { this.shopPolicies = shopPolicies; }
        public ShopAddressNode getBillingAddress() { return billingAddress; }
        public void setBillingAddress(ShopAddressNode billingAddress) { this.billingAddress = billingAddress; }
    }

    public static class ShopData {
        private ShopNode shop;
        public ShopNode getShop() { return shop; }
        public void setShop(ShopNode shop) { this.shop = shop; }
    }
}
