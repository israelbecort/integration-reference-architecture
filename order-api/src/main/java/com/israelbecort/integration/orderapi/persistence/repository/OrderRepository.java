package com.israelbecort.integration.orderapi.persistence.repository;

import com.israelbecort.integration.orderapi.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    Optional<OrderEntity> findByIdempotencyKey(UUID idempotencyKey);

    boolean existsByExternalOrderId(String externalOrderId);
}