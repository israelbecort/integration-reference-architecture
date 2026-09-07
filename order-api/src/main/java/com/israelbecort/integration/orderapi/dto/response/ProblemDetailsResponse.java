package com.israelbecort.integration.orderapi.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ProblemDetailsResponse(

        String type,
        String title,
        int status,
        String detail,
        String instance,
        String errorCode,
        UUID correlationId,
        Instant timestamp

) {
}