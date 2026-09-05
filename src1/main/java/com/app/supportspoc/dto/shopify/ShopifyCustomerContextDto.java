package com.app.supportspoc.dto.shopify;

import java.util.List;

/**
 * DTOs for CUSTOMER_CONTEXT and CUSTOMER_ORDER_CONTEXT: customer profile, addresses,
 * recent orders with their return/refund state, and metadata -- one aggregate query
 * instead of separate customer/orders/returns/refunds calls.
 */
public class ShopifyCustomerContextDto {

    public static class CustomerAddress {
        private String id;
        private String firstName;
        private String lastName;
        private String company;
        private String address1;
        private String address2;
        private String city;
        private String province;
        private String zip;
        private String countryCodeV2;
        private String phone;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getCompany() { return company; }
        public void setCompany(String company) { this.company = company; }
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

    public static class MetafieldEntry {
        private String namespace;
        private String key;
        private String value;

        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    /**
     * One order as seen from the customer context -- deliberately lighter than
     * OrderSupportContext (no full line items) since this rides inside a customer
     * query that may list up to 10 orders at once.
     */
    public static class CustomerOrderSummary {
        private String id;
        private String name;
        private String createdAt;
        private String displayFinancialStatus;
        private String displayFulfillmentStatus;
        private List<String> openReturnStatuses; // non-empty => this order has an open/requested return
        private boolean hasRefunds;

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
        public List<String> getOpenReturnStatuses() { return openReturnStatuses; }
        public void setOpenReturnStatuses(List<String> openReturnStatuses) { this.openReturnStatuses = openReturnStatuses; }
        public boolean isHasRefunds() { return hasRefunds; }
        public void setHasRefunds(boolean hasRefunds) { this.hasRefunds = hasRefunds; }
    }

    public static class CustomerContext {
        private String id;
        private String firstName;
        private String lastName;
        private String displayName;
        private String email;
        private String phone;
        private Long numberOfOrders;
        private ShopifyOrderDto.Money amountSpent;
        private CustomerAddress defaultAddress;
        private List<CustomerAddress> addresses;
        private List<CustomerOrderSummary> recentOrders;
        private List<String> tags;
        private String note;
        private List<MetafieldEntry> metafields;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public Long getNumberOfOrders() { return numberOfOrders; }
        public void setNumberOfOrders(Long numberOfOrders) { this.numberOfOrders = numberOfOrders; }
        public ShopifyOrderDto.Money getAmountSpent() { return amountSpent; }
        public void setAmountSpent(ShopifyOrderDto.Money amountSpent) { this.amountSpent = amountSpent; }
        public CustomerAddress getDefaultAddress() { return defaultAddress; }
        public void setDefaultAddress(CustomerAddress defaultAddress) { this.defaultAddress = defaultAddress; }
        public List<CustomerAddress> getAddresses() { return addresses; }
        public void setAddresses(List<CustomerAddress> addresses) { this.addresses = addresses; }
        public List<CustomerOrderSummary> getRecentOrders() { return recentOrders; }
        public void setRecentOrders(List<CustomerOrderSummary> recentOrders) { this.recentOrders = recentOrders; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public List<MetafieldEntry> getMetafields() { return metafields; }
        public void setMetafields(List<MetafieldEntry> metafields) { this.metafields = metafields; }
    }
}
