package com.israelbecort.integration.orderapi.client.integration.dto;

import java.time.Instant;
import java.util.UUID;

public record ProcessOrderAcceptedResponse(
        UUID orderId,
        String status,
        UUID correlationId,
        Instant processingAcceptedAt
) {
}