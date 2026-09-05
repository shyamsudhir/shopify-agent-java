package com.app.supportspoc.dto.shopify;

import java.util.List;

public class ShopifyCustomerContextResponseDto {

    public static class EmailRef { private String emailAddress; public String getEmailAddress() { return emailAddress; } public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; } }
    public static class PhoneRef { private String phoneNumber; public String getPhoneNumber() { return phoneNumber; } public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; } }

    public static class AddressNode {
        private String id, firstName, lastName, company, address1, address2, city, province, zip, countryCodeV2, phone;
        public String getId() { return id; } public void setId(String id) { this.id = id; }
        public String getFirstName() { return firstName; } public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; } public void setLastName(String lastName) { this.lastName = lastName; }
        public String getCompany() { return company; } public void setCompany(String company) { this.company = company; }
        public String getAddress1() { return address1; } public void setAddress1(String address1) { this.address1 = address1; }
        public String getAddress2() { return address2; } public void setAddress2(String address2) { this.address2 = address2; }
        public String getCity() { return city; } public void setCity(String city) { this.city = city; }
        public String getProvince() { return province; } public void setProvince(String province) { this.province = province; }
        public String getZip() { return zip; } public void setZip(String zip) { this.zip = zip; }
        public String getCountryCodeV2() { return countryCodeV2; } public void setCountryCodeV2(String countryCodeV2) { this.countryCodeV2 = countryCodeV2; }
        public String getPhone() { return phone; } public void setPhone(String phone) { this.phone = phone; }
    }

    public static class ReturnRef { private String status; public String getStatus() { return status; } public void setStatus(String status) { this.status = status; } }
    public static class ReturnConnectionRef { private List<Edge<ReturnRef>> edges; public List<Edge<ReturnRef>> getEdges() { return edges; } public void setEdges(List<Edge<ReturnRef>> edges) { this.edges = edges; } }
    public static class Edge<T> { private T node; public T getNode() { return node; } public void setNode(T node) { this.node = node; } }

    public static class RefundRef { private String id; public String getId() { return id; } public void setId(String id) { this.id = id; } }

    public static class CustomerOrderNode {
        private String id, name, createdAt, displayFinancialStatus, displayFulfillmentStatus;
        private ReturnConnectionRef returns;
        private List<RefundRef> refunds;
        public String getId() { return id; } public void setId(String id) { this.id = id; }
        public String getName() { return name; } public void setName(String name) { this.name = name; }
        public String getCreatedAt() { return createdAt; } public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getDisplayFinancialStatus() { return displayFinancialStatus; } public void setDisplayFinancialStatus(String v) { this.displayFinancialStatus = v; }
        public String getDisplayFulfillmentStatus() { return displayFulfillmentStatus; } public void setDisplayFulfillmentStatus(String v) { this.displayFulfillmentStatus = v; }
        public ReturnConnectionRef getReturns() { return returns; } public void setReturns(ReturnConnectionRef returns) { this.returns = returns; }
        public List<RefundRef> getRefunds() { return refunds; } public void setRefunds(List<RefundRef> refunds) { this.refunds = refunds; }
    }

    public static class CustomerOrderConnection { private List<Edge<CustomerOrderNode>> edges; public List<Edge<CustomerOrderNode>> getEdges() { return edges; } public void setEdges(List<Edge<CustomerOrderNode>> edges) { this.edges = edges; } }

    public static class MetafieldNode {
        private String namespace, key, value;
        public String getNamespace() { return namespace; } public void setNamespace(String namespace) { this.namespace = namespace; }
        public String getKey() { return key; } public void setKey(String key) { this.key = key; }
        public String getValue() { return value; } public void setValue(String value) { this.value = value; }
    }
    public static class MetafieldConnection { private List<Edge<MetafieldNode>> edges; public List<Edge<MetafieldNode>> getEdges() { return edges; } public void setEdges(List<Edge<MetafieldNode>> edges) { this.edges = edges; } }

    public static class CustomerContextNode {
        private String id, firstName, lastName, displayName, note;
        private EmailRef defaultEmailAddress;
        private PhoneRef defaultPhoneNumber;
        private Long numberOfOrders;
        private ShopifyOrderDto.Money amountSpent;
        private AddressNode defaultAddress;
        private List<AddressNode> addresses;
        private CustomerOrderConnection orders;
        private List<String> tags;
        private MetafieldConnection metafields;

        public String getId() { return id; } public void setId(String id) { this.id = id; }
        public String getFirstName() { return firstName; } public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; } public void setLastName(String lastName) { this.lastName = lastName; }
        public String getDisplayName() { return displayName; } public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getNote() { return note; } public void setNote(String note) { this.note = note; }
        public EmailRef getDefaultEmailAddress() { return defaultEmailAddress; } public void setDefaultEmailAddress(EmailRef v) { this.defaultEmailAddress = v; }
        public PhoneRef getDefaultPhoneNumber() { return defaultPhoneNumber; } public void setDefaultPhoneNumber(PhoneRef v) { this.defaultPhoneNumber = v; }
        public Long getNumberOfOrders() { return numberOfOrders; } public void setNumberOfOrders(Long numberOfOrders) { this.numberOfOrders = numberOfOrders; }
        public ShopifyOrderDto.Money getAmountSpent() { return amountSpent; } public void setAmountSpent(ShopifyOrderDto.Money amountSpent) { this.amountSpent = amountSpent; }
        public AddressNode getDefaultAddress() { return defaultAddress; } public void setDefaultAddress(AddressNode defaultAddress) { this.defaultAddress = defaultAddress; }
        public List<AddressNode> getAddresses() { return addresses; } public void setAddresses(List<AddressNode> addresses) { this.addresses = addresses; }
        public CustomerOrderConnection getOrders() { return orders; } public void setOrders(CustomerOrderConnection orders) { this.orders = orders; }
        public List<String> getTags() { return tags; } public void setTags(List<String> tags) { this.tags = tags; }
        public MetafieldConnection getMetafields() { return metafields; } public void setMetafields(MetafieldConnection metafields) { this.metafields = metafields; }
    }

    public static class CustomerContextData {
        private CustomerContextNode customer;
        public CustomerContextNode getCustomer() { return customer; }
        public void setCustomer(CustomerContextNode customer) { this.customer = customer; }
    }
}
