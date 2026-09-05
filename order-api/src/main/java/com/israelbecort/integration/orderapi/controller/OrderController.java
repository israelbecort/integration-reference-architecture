package com.israelbecort.integration.orderapi.controller;

import com.israelbecort.integration.orderapi.dto.request.OrderRequest;
import com.israelbecort.integration.orderapi.dto.response.OrderAcceptedResponse;
import com.israelbecort.integration.orderapi.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderAcceptedResponse> createOrder(
            @RequestHeader(value = "X-Correlation-Id", required = false)
            UUID correlationId,

            @RequestHeader("Idempotency-Key")
            UUID idempotencyKey,

            @Valid
            @RequestBody
            OrderRequest request
    ) {

        UUID effectiveCorrelationId =
                correlationId != null
                        ? correlationId
                        : UUID.randomUUID();

        OrderAcceptedResponse response =
                orderService.acceptOrder(
                        request,
                        idempotencyKey,
                        effectiveCorrelationId
                );

        return ResponseEntity
                .accepted()
                .header(
                        "X-Correlation-Id",
                        effectiveCorrelationId.toString()
                )
                .body(response);
    }

}