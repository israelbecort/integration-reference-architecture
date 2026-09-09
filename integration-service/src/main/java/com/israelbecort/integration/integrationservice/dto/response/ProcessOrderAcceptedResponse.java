package com.israelbecort.integration.integrationservice.dto.response;

import com.israelbecort.integration.integrationservice.domain.IntegrationOrderStatus;

import java.time.Instant;
import java.util.UUID;

public record ProcessOrderAcceptedResponse(

        UUID orderId,

        IntegrationOrderStatus status,

        UUID correlationId,

        Instant processingAcceptedAt

) {
}