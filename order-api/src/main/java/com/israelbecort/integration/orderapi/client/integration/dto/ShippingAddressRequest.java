package com.israelbecort.integration.orderapi.client.integration.dto;

public record ShippingAddressRequest(
        String addressLine1,
        String addressLine2,
        String city,
        String postalCode,
        String country
) {
}