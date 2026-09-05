package com.app.supportspoc.util.shopify;

import com.app.supportspoc.dto.shopify.ShopifyCustomerContextDto.*;
import com.app.supportspoc.dto.shopify.ShopifyCustomerContextResponseDto.*;
import com.app.supportspoc.dto.shopify.ShopifyDto.GraphQLRequest;
import com.app.supportspoc.dto.shopify.ShopifyDto.GraphQLResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CUSTOMER_CONTEXT and CUSTOMER_ORDER_CONTEXT: customer profile, addresses, recent
 * orders (with their open-return / refund state), and metadata in ONE GraphQL request,
 * per the "aggregate query, not chained getters" principle -- recentOrders' returns and
 * refunds are nested under customer.orders in the same query, not separate calls.
 *
 * customerId here is the customer's own gid or numeric id -- this method IS the
 * authorization boundary for everything about that customer, so callers must already
 * have verified the caller is allowed to see this customerId (e.g. it came from the
 * authenticated session, not from free-text user input).
 */
@Service
public class ShopifyCustomerContextService {

    private final ShopifyGraphQLClient client;

    @Autowired
    public ShopifyCustomerContextService(ShopifyGraphQLClient client) {
        this.client = client;
    }

    private static final String CUSTOMER_CONTEXT_QUERY =
            "query GetCustomerContext($id: ID!) { customer(id: $id) { " +
            "id firstName lastName displayName note tags " +
            "defaultEmailAddress { emailAddress } defaultPhoneNumber { phoneNumber } " +
            "numberOfOrders amountSpent { amount currencyCode } " +
            "defaultAddress { id firstName lastName company address1 address2 city province zip countryCodeV2 phone } " +
            "addresses { id firstName lastName company address1 address2 city province zip countryCodeV2 phone } " +
            "orders(first: 10, sortKey: CREATED_AT, reverse: true) { edges { node { " +
            "  id name createdAt displayFinancialStatus displayFulfillmentStatus " +
            "  returns(first: 5, query: \"status:OPEN OR status:REQUESTED\") { edges { node { status } } } " +
            "  refunds(first: 1) { id } } } } " +
            "metafields(first: 20) { edges { node { namespace key value } } } " +
            "} }";

    public CustomerContext getCustomerContext(String shopDomain, String accessToken, String customerGid)
            throws IOException, InterruptedException {
        String endpoint = "https://" + shopDomain + "/admin/api/2026-07/graphql.json";
        GraphQLRequest payload = new GraphQLRequest(CUSTOMER_CONTEXT_QUERY, Collections.singletonMap("id", customerGid));

        CustomerContextData data = client.execute(endpoint, accessToken, payload,
                new TypeReference<GraphQLResponse<CustomerContextData>>() {});

        CustomerContextNode node = data.getCustomer();
        if (node == null) {
            return null;
        }

        CustomerContext context = new CustomerContext();
        context.setId(node.getId());
        context.setFirstName(node.getFirstName());
        context.setLastName(node.getLastName());
        context.setDisplayName(node.getDisplayName());
        context.setEmail(node.getDefaultEmailAddress() != null ? node.getDefaultEmailAddress().getEmailAddress() : null);
        context.setPhone(node.getDefaultPhoneNumber() != null ? node.getDefaultPhoneNumber().getPhoneNumber() : null);
        context.setNumberOfOrders(node.getNumberOfOrders());
        context.setAmountSpent(node.getAmountSpent());
        context.setDefaultAddress(mapAddress(node.getDefaultAddress()));
        context.setTags(node.getTags());
        context.setNote(node.getNote());

        if (node.getAddresses() != null) {
            context.setAddresses(node.getAddresses().stream().map(this::mapAddress).collect(Collectors.toList()));
        }

        List<CustomerOrderSummary> recentOrders = new ArrayList<>();
        if (node.getOrders() != null && node.getOrders().getEdges() != null) {
            for (Edge<CustomerOrderNode> edge : node.getOrders().getEdges()) {
                CustomerOrderNode orderNode = edge.getNode();
                CustomerOrderSummary summary = new CustomerOrderSummary();
                summary.setId(orderNode.getId());
                summary.setName(orderNode.getName());
                summary.setCreatedAt(orderNode.getCreatedAt());
                summary.setDisplayFinancialStatus(orderNode.getDisplayFinancialStatus());
                summary.setDisplayFulfillmentStatus(orderNode.getDisplayFulfillmentStatus());
                if (orderNode.getReturns() != null && orderNode.getReturns().getEdges() != null) {
                    summary.setOpenReturnStatuses(orderNode.getReturns().getEdges().stream()
                            .map(e -> e.getNode().getStatus())
                            .collect(Collectors.toList()));
                } else {
                    summary.setOpenReturnStatuses(Collections.emptyList());
                }
                summary.setHasRefunds(orderNode.getRefunds() != null && !orderNode.getRefunds().isEmpty());
                recentOrders.add(summary);
            }
        }
        context.setRecentOrders(recentOrders);

        List<MetafieldEntry> metafields = new ArrayList<>();
        if (node.getMetafields() != null && node.getMetafields().getEdges() != null) {
            for (Edge<MetafieldNode> edge : node.getMetafields().getEdges()) {
                MetafieldEntry entry = new MetafieldEntry();
                entry.setNamespace(edge.getNode().getNamespace());
                entry.setKey(edge.getNode().getKey());
                entry.setValue(edge.getNode().getValue());
                metafields.add(entry);
            }
        }
        context.setMetafields(metafields);

        return context;
    }

    /**
     * CUSTOMER_ORDER_CONTEXT is satisfied by getCustomerContext(...).getRecentOrders() --
     * not a separate query. If you need more than the last 10 orders or a date/status
     * filter, use ShopifyGraphQLService.searchOrders(...) instead (ORDER_SEARCH_CONTEXT),
     * which already supports that.
     */

    private CustomerAddress mapAddress(AddressNode node) {
        if (node == null) {
            return null;
        }
        CustomerAddress address = new CustomerAddress();
        address.setId(node.getId());
        address.setFirstName(node.getFirstName());
        address.setLastName(node.getLastName());
        address.setCompany(node.getCompany());
        address.setAddress1(node.getAddress1());
        address.setAddress2(node.getAddress2());
        address.setCity(node.getCity());
        address.setProvince(node.getProvince());
        address.setZip(node.getZip());
        address.setCountryCodeV2(node.getCountryCodeV2());
        address.setPhone(node.getPhone());
        return address;
    }
}
