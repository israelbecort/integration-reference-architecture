package com.israelbecort.integration.orderapi.service;

import com.israelbecort.integration.orderapi.domain.OrderStatus;
import com.israelbecort.integration.orderapi.dto.request.OrderRequest;
import com.israelbecort.integration.orderapi.dto.response.OrderAcceptedResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderService {

    public OrderAcceptedResponse acceptOrder(
            OrderRequest request,
            UUID idempotencyKey,
            UUID correlationId
    ) {

        UUID orderId = UUID.randomUUID();

        return new OrderAcceptedResponse(
                orderId,
                request.externalOrderId(),
                OrderStatus.ACCEPTED,
                correlationId,
                Instant.now()
        );
    }

}