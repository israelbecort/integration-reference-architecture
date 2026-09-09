package com.israelbecort.integration.orderapi.service;

import com.israelbecort.integration.orderapi.domain.OrderStatus;
import com.israelbecort.integration.orderapi.dto.request.OrderRequest;
import com.israelbecort.integration.orderapi.exception.OrderConflictException;
import com.israelbecort.integration.orderapi.persistence.entity.OrderEntity;
import com.israelbecort.integration.orderapi.persistence.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderPersistenceService {

    private final OrderRepository orderRepository;

    public OrderPersistenceService(
            OrderRepository orderRepository
    ) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderEntity findOrCreateAcceptedOrder(
            OrderRequest request,
            UUID idempotencyKey,
            UUID correlationId,
            String requestHash
    ) {

        Optional<OrderEntity> existingOrder =
                orderRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOrder.isPresent()) {

            OrderEntity order = existingOrder.get();

            if (!order.getRequestHash().equals(requestHash)) {
                throw new OrderConflictException(
                        "The Idempotency-Key has already been used with a different request."
                );
            }

            return order;
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

        return orderRepository.saveAndFlush(order);
    }

    @Transactional
    public OrderEntity markAsProcessing(
            UUID orderId
    ) {

        OrderEntity order =
                orderRepository.findById(orderId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Order not found: " + orderId
                                )
                        );

        if (order.getStatus() == OrderStatus.ACCEPTED) {
            order.markAsProcessing();
        }

        return orderRepository.saveAndFlush(order);
    }
}