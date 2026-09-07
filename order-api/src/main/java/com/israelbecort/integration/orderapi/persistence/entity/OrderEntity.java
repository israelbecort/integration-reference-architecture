package com.israelbecort.integration.orderapi.persistence.entity;

import com.israelbecort.integration.orderapi.domain.OrderStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_orders_idempotency_key",
                        columnNames = "idempotency_key"
                ),
                @UniqueConstraint(
                        name = "uk_orders_external_order_id",
                        columnNames = "external_order_id"
                )
        }
)
public class OrderEntity {

    @Id
    @Column(
            name = "order_id",
            nullable = false,
            updatable = false
    )
    private UUID orderId;

    @Column(
            name = "external_order_id",
            nullable = false,
            length = 100
    )
    private String externalOrderId;

    @Column(
            name = "idempotency_key",
            nullable = false,
            updatable = false
    )
    private UUID idempotencyKey;

    @Column(
            name = "request_hash",
            nullable = false,
            updatable = false,
            length = 64
    )
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private OrderStatus status;

    @Column(
            name = "correlation_id",
            nullable = false
    )
    private UUID correlationId;

    @Column(
            name = "accepted_at",
            nullable = false,
            updatable = false
    )
    private Instant acceptedAt;

    protected OrderEntity() {
    }

    public OrderEntity(
            UUID orderId,
            String externalOrderId,
            UUID idempotencyKey,
            String requestHash,
            OrderStatus status,
            UUID correlationId,
            Instant acceptedAt
    ) {
        this.orderId = orderId;
        this.externalOrderId = externalOrderId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = status;
        this.correlationId = correlationId;
        this.acceptedAt = acceptedAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}