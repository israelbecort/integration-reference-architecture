package com.israelbecort.integration.orderapi.service;

import com.israelbecort.integration.orderapi.domain.OrderStatus;
import com.israelbecort.integration.orderapi.dto.request.OrderRequest;
import com.israelbecort.integration.orderapi.dto.response.OrderAcceptedResponse;
import com.israelbecort.integration.orderapi.exception.OrderConflictException;
import com.israelbecort.integration.orderapi.persistence.entity.OrderEntity;
import com.israelbecort.integration.orderapi.persistence.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderRequestHashCalculator requestHashCalculator;

    public OrderService(
            OrderRepository orderRepository,
            OrderRequestHashCalculator requestHashCalculator
    ) {
        this.orderRepository = orderRepository;
        this.requestHashCalculator = requestHashCalculator;
    }

    @Transactional
    public OrderAcceptedResponse acceptOrder(
            OrderRequest request,
            UUID idempotencyKey,
            UUID correlationId
    ) {

        String requestHash =
                requestHashCalculator.calculate(request);

        Optional<OrderEntity> existingOrder =
                orderRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOrder.isPresent()) {

            OrderEntity order = existingOrder.get();

            if (!order.getRequestHash().equals(requestHash)) {

                throw new OrderConflictException(
                        "The Idempotency-Key has already been used with a different request."
                );
            }

            return toResponse(
                    order,
                    correlationId
            );
        }

        if (orderRepository.existsByExternalOrderId(
                request.externalOrderId()
        )) {

            throw new OrderConflictException(
                    "An order with the same externalOrderId already exists."
            );
        }

        OrderEntity order =
                new OrderEntity(
                        UUID.randomUUID(),
                        request.externalOrderId(),
                        idempotencyKey,
                        requestHash,
                        OrderStatus.ACCEPTED,
                        correlationId,
                        Instant.now()
                );

        OrderEntity savedOrder =
                orderRepository.saveAndFlush(order);

        return toResponse(
                savedOrder,
                correlationId
        );
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