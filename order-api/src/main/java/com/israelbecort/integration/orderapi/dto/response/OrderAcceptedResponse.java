package com.israelbecort.integration.orderapi.dto.response;

import com.israelbecort.integration.orderapi.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderAcceptedResponse(

        UUID orderId,
        String externalOrderId,
        OrderStatus status,
        UUID correlationId,
        Instant acceptedAt

) {
}