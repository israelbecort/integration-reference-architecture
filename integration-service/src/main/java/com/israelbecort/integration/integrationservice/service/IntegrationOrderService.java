package com.israelbecort.integration.integrationservice.service;

import com.israelbecort.integration.integrationservice.domain.IntegrationOrderStatus;
import com.israelbecort.integration.integrationservice.dto.request.ProcessOrderRequest;
import com.israelbecort.integration.integrationservice.dto.response.ProcessOrderAcceptedResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class IntegrationOrderService {

    public ProcessOrderAcceptedResponse processOrder(
            UUID orderId,
            UUID correlationId,
            ProcessOrderRequest request
    ) {

        return new ProcessOrderAcceptedResponse(
                orderId,
                IntegrationOrderStatus.PROCESSING,
                correlationId,
                Instant.now()
        );
    }
}