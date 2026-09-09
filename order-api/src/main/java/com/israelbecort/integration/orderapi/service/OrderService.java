package com.israelbecort.integration.orderapi.service;

import com.israelbecort.integration.orderapi.client.integration.IntegrationServiceClient;
import com.israelbecort.integration.orderapi.client.integration.dto.ProcessOrderAcceptedResponse;
import com.israelbecort.integration.orderapi.client.integration.dto.ProcessOrderRequest;
import com.israelbecort.integration.orderapi.client.integration.mapper.IntegrationOrderMapper;
import com.israelbecort.integration.orderapi.domain.OrderStatus;
import com.israelbecort.integration.orderapi.dto.request.OrderRequest;
import com.israelbecort.integration.orderapi.dto.response.OrderAcceptedResponse;
import com.israelbecort.integration.orderapi.persistence.entity.OrderEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderService {

    private final OrderRequestHashCalculator requestHashCalculator;
    private final OrderPersistenceService orderPersistenceService;
    private final IntegrationOrderMapper integrationOrderMapper;
    private final IntegrationServiceClient integrationServiceClient;

    public OrderService(
            OrderRequestHashCalculator requestHashCalculator,
            OrderPersistenceService orderPersistenceService,
            IntegrationOrderMapper integrationOrderMapper,
            IntegrationServiceClient integrationServiceClient
    ) {
        this.requestHashCalculator = requestHashCalculator;
        this.orderPersistenceService = orderPersistenceService;
        this.integrationOrderMapper = integrationOrderMapper;
        this.integrationServiceClient = integrationServiceClient;
    }

    public OrderAcceptedResponse acceptOrder(
            OrderRequest request,
            UUID idempotencyKey,
            UUID correlationId
    ) {

        String requestHash =
                requestHashCalculator.calculate(request);

        OrderEntity order =
                orderPersistenceService.findOrCreateAcceptedOrder(
                        request,
                        idempotencyKey,
                        correlationId,
                        requestHash
                );

        if (order.getStatus() == OrderStatus.ACCEPTED) {

            ProcessOrderRequest processOrderRequest =
                    integrationOrderMapper.toProcessOrderRequest(
                            request,
                            order.getAcceptedAt()
                    );

            ProcessOrderAcceptedResponse integrationResponse =
                    integrationServiceClient.processOrder(
                            order.getOrderId(),
                            correlationId,
                            processOrderRequest
                    );

            validateIntegrationResponse(
                    order,
                    correlationId,
                    integrationResponse
            );

            order =
                    orderPersistenceService.markAsProcessing(
                            order.getOrderId()
                    );
        }

        return toResponse(
                order,
                correlationId
        );
    }

    private void validateIntegrationResponse(
            OrderEntity order,
            UUID correlationId,
            ProcessOrderAcceptedResponse response
    ) {

        if (!order.getOrderId().equals(response.orderId())) {
            throw new IllegalStateException(
                    "Integration Service returned a different orderId."
            );
        }

        if (!correlationId.equals(response.correlationId())) {
            throw new IllegalStateException(
                    "Integration Service returned a different correlationId."
            );
        }

        if (!OrderStatus.PROCESSING.name().equals(response.status())) {
            throw new IllegalStateException(
                    "Integration Service returned unexpected order status: "
                            + response.status()
            );
        }
    }

    private OrderAcceptedResponse toResponse(
            OrderEntity order,
            UUID correlationId
    ) {

        return new OrderAcceptedResponse(
                order.getOrderId(),
                order.getExternalOrderId(),
                order.getStatus(),
                correlationId,
                order.getAcceptedAt()
        );
    }
}