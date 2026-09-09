package com.israelbecort.integration.orderapi.client.integration.dto;

import java.math.BigDecimal;

public record OrderItemRequest(
        String productId,
        Integer quantity,
        BigDecimal unitPrice
) {
}