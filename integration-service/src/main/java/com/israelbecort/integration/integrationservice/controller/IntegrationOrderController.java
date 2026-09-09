package com.israelbecort.integration.integrationservice.controller;

import com.israelbecort.integration.integrationservice.dto.request.ProcessOrderRequest;
import com.israelbecort.integration.integrationservice.dto.response.ProcessOrderAcceptedResponse;
import com.israelbecort.integration.integrationservice.service.IntegrationOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/orders")
public class IntegrationOrderController {

    private final IntegrationOrderService integrationOrderService;

    public IntegrationOrderController(
            IntegrationOrderService integrationOrderService
    ) {
        this.integrationOrderService = integrationOrderService;
    }

    @PostMapping("/{orderId}/process")
    public ResponseEntity<ProcessOrderAcceptedResponse> processOrder(
            @PathVariable UUID orderId,
            @RequestHeader("X-Correlation-Id") UUID correlationId,
            @Valid @RequestBody ProcessOrderRequest request
    ) {

        ProcessOrderAcceptedResponse response =
                integrationOrderService.processOrder(
                        orderId,
                        correlationId,
                        request
                );

        return ResponseEntity
                .accepted()
                .header(
                        "X-Correlation-Id",
                        correlationId.toString()
                )
                .body(response);
    }
}