package com.app.supportspoc.dto.shopify;

import java.util.List;

/**
 * DTOs for:
 *  - Returns & Refunds: check_return_eligibility, get_return_policy, get_returnable_items,
 *    create_return, return_reason (folded into create_return), return_status, refund_status,
 *    refund_details, request_refund (folded into create_return -- see service javadoc),
 *    check_refund_eligibility (reuses ShopifyOrderDto.OrderDetail.refundable, no new type needed)
 *  - Product search/catalog: search by title/keyword/type/price/availability/variant,
 *    product_details, product_variants, product_inventory, product_images,
 *    product_recommendations (heuristic -- see service javadoc)
 *  - Store/product policies: get_return_policy, get_shipping_policy, get_contact_information,
 *    (get_store_hours, get_store_location, get_exchange_policy, get_payment_policy,
 *    get_warranty_policy are NOT modeled here -- see service javadoc for why)
 */
public class ShopifyCatalogDto {

    // =====================================================================
    // Returns
    // =====================================================================

    public static class ReturnableFulfillmentLineItem {
        private String fulfillmentLineItemId; // gid://shopify/FulfillmentLineItem/... -- required input to create a return
        private String lineItemId;
        private String title;
        private String sku;
        private Integer returnableQuantity;

        public String getFulfillmentLineItemId() { return fulfillmentLineItemId; }
        public void setFulfillmentLineItemId(String fulfillmentLineItemId) { this.fulfillmentLineItemId = fulfillmentLineItemId; }
        public String getLineItemId() { return lineItemId; }
        public void setLineItemId(String lineItemId) { this.lineItemId = lineItemId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Integer getReturnableQuantity() { return returnableQuantity; }
        public void setReturnableQuantity(Integer returnableQuantity) { this.returnableQuantity = returnableQuantity; }
    }

    public static class ReturnEligibilityResult {
        private boolean eligible;
        private List<String> reasons;
        private List<ReturnableFulfillmentLineItem> returnableItems;

        public boolean isEligible() { return eligible; }
        public void setEligible(boolean eligible) { this.eligible = eligible; }
        public List<String> getReasons() { return reasons; }
        public void setReasons(List<String> reasons) { this.reasons = reasons; }
        public List<ReturnableFulfillmentLineItem> getReturnableItems() { return returnableItems; }
        public void setReturnableItems(List<ReturnableFulfillmentLineItem> returnableItems) { this.returnableItems = returnableItems; }
    }

    /** One requested line within create_return. Reason + customer note live here, not as a separate tool. */
    public static class ReturnLineItemRequest {
        private String fulfillmentLineItemId; // from ReturnableFulfillmentLineItem.fulfillmentLineItemId
        private int quantity;
        private String reason;       // legacy ReturnReason-style string, e.g. WRONG_ITEM, SIZE_TOO_SMALL, SIZE_TOO_LARGE,
                                      // DEFECTIVE, NOT_AS_DESCRIBED, UNWANTED, OTHER -- verify against the store's
                                      // configured return reasons, see service javadoc
        private String customerNote; // free text, e.g. "I received the wrong size"

        public String getFulfillmentLineItemId() { return fulfillmentLineItemId; }
        public void setFulfillmentLineItemId(String fulfillmentLineItemId) { this.fulfillmentLineItemId = fulfillmentLineItemId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getCustomerNote() { return customerNote; }
        public void setCustomerNote(String customerNote) { this.customerNote = customerNote; }
    }

    public static class ReturnRequestInput {
        private String orderId;      // gid://shopify/Order/... -- resolve via getOrderDetails first
        private List<ReturnLineItemRequest> lineItems;

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public List<ReturnLineItemRequest> getLineItems() { return lineItems; }
        public void setLineItems(List<ReturnLineItemRequest> lineItems) { this.lineItems = lineItems; }
    }

    public static class ReturnRequestResult {
        private boolean submitted;
        private String returnId;
        private String status;       // ReturnStatus: REQUESTED on success
        private List<String> errors;

        public boolean isSubmitted() { return submitted; }
        public void setSubmitted(boolean submitted) { this.submitted = submitted; }
        public String getReturnId() { return returnId; }
        public void setReturnId(String returnId) { this.returnId = returnId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }

    public static class ReturnSummary {
        private String id;
        private String name;
        private String status;       // ReturnStatus: REQUESTED, OPEN, CLOSED, DECLINED, CANCELED
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

    // =====================================================================
    // Refunds (read-only for this agent -- see service javadoc)
    // =====================================================================

    public static class RefundSummary {
        private String id;
        private String createdAt;
        private String processedAt;
        private String note;
        private ShopifyOrderDto.MoneyBag totalRefundedSet;
        private List<String> transactionStatuses; // OrderTransaction.status per transaction: pending/success/failure/...

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
        public List<String> getTransactionStatuses() { return transactionStatuses; }
        public void setTransactionStatuses(List<String> transactionStatuses) { this.transactionStatuses = transactionStatuses; }
    }

    // =====================================================================
    // Products
    // =====================================================================

    public static class ProductSearchFilter {
        private String keyword;         // free-text, matches Shopify's default multi-field search
        private String title;
        private String productType;
        private String tag;
        private String vendor;
        private String priceMin;        // maps to "price:>=" -- see service javadoc caveat
        private String priceMax;        // maps to "price:<="
        private Boolean availableOnly;  // maps to out_of_stock_somewhere:false (best-effort, see javadoc)
        private Integer limit;

        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }
        public String getTag() { return tag; }
        public void setTag(String tag) { this.tag = tag; }
        public String getVendor() { return vendor; }
        public void setVendor(String vendor) { this.vendor = vendor; }
        public String getPriceMin() { return priceMin; }
        public void setPriceMin(String priceMin) { this.priceMin = priceMin; }
        public String getPriceMax() { return priceMax; }
        public void setPriceMax(String priceMax) { this.priceMax = priceMax; }
        public Boolean getAvailableOnly() { return availableOnly; }
        public void setAvailableOnly(Boolean availableOnly) { this.availableOnly = availableOnly; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
    }

    public static class SelectedOption {
        private String name;   // e.g. "Color", "Size"
        private String value;  // e.g. "Red", "M"

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public static class ProductVariantDetail {
        private String id;
        private String title;
        private String sku;
        private String price;
        private Boolean availableForSale;
        private Integer inventoryQuantity;
        private List<SelectedOption> selectedOptions;
        private String imageUrl;

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
        public List<SelectedOption> getSelectedOptions() { return selectedOptions; }
        public void setSelectedOptions(List<SelectedOption> selectedOptions) { this.selectedOptions = selectedOptions; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }

    public static class ProductImage {
        private String url;
        private String altText;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getAltText() { return altText; }
        public void setAltText(String altText) { this.altText = altText; }
    }

    public static class ProductSummary {
        private String id;
        private String title;
        private String productType;
        private String status;
        private Integer totalInventory;
        private String minPrice;
        private String maxPrice;
        private String featuredImageUrl;

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
        public String getMinPrice() { return minPrice; }
        public void setMinPrice(String minPrice) { this.minPrice = minPrice; }
        public String getMaxPrice() { return maxPrice; }
        public void setMaxPrice(String maxPrice) { this.maxPrice = maxPrice; }
        public String getFeaturedImageUrl() { return featuredImageUrl; }
        public void setFeaturedImageUrl(String featuredImageUrl) { this.featuredImageUrl = featuredImageUrl; }
    }

    public static class ProductDetail {
        private String id;
        private String title;
        private String descriptionHtml;
        private String productType;
        private String vendor;
        private String status;
        private Integer totalInventory;
        private List<ProductImage> images;
        private List<ProductVariantDetail> variants;

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
        public List<ProductImage> getImages() { return images; }
        public void setImages(List<ProductImage> images) { this.images = images; }
        public List<ProductVariantDetail> getVariants() { return variants; }
        public void setVariants(List<ProductVariantDetail> variants) { this.variants = variants; }
    }

    // =====================================================================
    // Shop policies (only the types Shopify actually models -- see service javadoc)
    // =====================================================================

    public static class ShopPolicy {
        private String type;   // ShopPolicyType: REFUND_POLICY, SHIPPING_POLICY, CONTACT_INFORMATION,
                                // PRIVACY_POLICY, TERMS_OF_SERVICE, TERMS_OF_SALE, SUBSCRIPTION_POLICY, LEGAL_NOTICE
        private String title;
        private String bodyHtml;
        private String url;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getBodyHtml() { return bodyHtml; }
        public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public static class StoreAddress {
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
}
