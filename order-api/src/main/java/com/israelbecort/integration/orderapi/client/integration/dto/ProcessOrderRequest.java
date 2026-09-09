package com.israelbecort.integration.orderapi.client.integration.dto;

import java.time.Instant;
import java.util.List;

public record ProcessOrderRequest(
        String externalOrderId,
        CustomerRequest customer,
        List<OrderItemRequest> items,
        String currency,
        ShippingAddressRequest shippingAddress,
        Instant acceptedAt
) {
}