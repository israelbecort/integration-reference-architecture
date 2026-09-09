package com.israelbecort.integration.orderapi.client.integration.dto;

public record CustomerRequest(
        String customerId,
        String email
) {
}