package com.israelbecort.integration.orderapi.client.integration;

import com.israelbecort.integration.orderapi.client.integration.dto.CustomerRequest;
import com.israelbecort.integration.orderapi.client.integration.dto.OrderItemRequest;
import com.israelbecort.integration.orderapi.client.integration.dto.ProcessOrderAcceptedResponse;
import com.israelbecort.integration.orderapi.client.integration.dto.ProcessOrderRequest;
import com.israelbecort.integration.orderapi.client.integration.dto.ShippingAddressRequest;
import com.israelbecort.integration.orderapi.exception.IntegrationServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class IntegrationServiceClientTest {

    private static final String BASE_URL =
            "http://integration-service";

    private MockRestServiceServer mockServer;

    private IntegrationServiceClient integrationServiceClient;

    @BeforeEach
    void setUp() {

        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl(BASE_URL);

        mockServer =
                MockRestServiceServer
                        .bindTo(builder)
                        .build();

        integrationServiceClient =
                new IntegrationServiceClient(
                        builder.build()
                );
    }

    @Test
    void processOrder_shouldReturnResponseWhenIntegrationServiceReturns202() {

        UUID orderId =
                UUID.randomUUID();

        UUID correlationId =
                UUID.randomUUID();

        mockServer.expect(
                        requestTo(
                                BASE_URL
                                        + "/internal/v1/orders/"
                                        + orderId
                                        + "/process"
                        )
                )
                .andExpect(
                        method(HttpMethod.POST)
                )
                .andExpect(
                        header(
                                "X-Correlation-Id",
                                correlationId.toString()
                        )
                )
                .andRespond(
                        withStatus(HttpStatus.ACCEPTED)
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .body(
                                        """
                                        {
                                          "orderId": "%s",
                                          "status": "PROCESSING",
                                          "correlationId": "%s",
                                          "processingAcceptedAt": "2026-09-08T10:00:00Z"
                                        }
                                        """.formatted(
                                                orderId,
                                                correlationId
                                        )
                                )
                );

        ProcessOrderAcceptedResponse response =
                integrationServiceClient.processOrder(
                        orderId,
                        correlationId,
                        processOrderRequest()
                );

        assertEquals(
                orderId,
                response.orderId()
        );

        assertEquals(
                correlationId,
                response.correlationId()
        );

        assertEquals(
                "PROCESSING",
                response.status()
        );

        mockServer.verify();
    }

    @Test
    void processOrder_shouldThrowUnavailableExceptionWhenIntegrationServiceReturns5xx() {

        UUID orderId =
                UUID.randomUUID();

        UUID correlationId =
                UUID.randomUUID();

        mockServer.expect(
                        requestTo(
                                BASE_URL
                                        + "/internal/v1/orders/"
                                        + orderId
                                        + "/process"
                        )
                )
                .andRespond(
                        withStatus(
                                HttpStatus.SERVICE_UNAVAILABLE
                        )
                );

        assertThrows(
                IntegrationServiceUnavailableException.class,
                () ->
                        integrationServiceClient.processOrder(
                                orderId,
                                correlationId,
                                processOrderRequest()
                        )
        );

        mockServer.verify();
    }

    @Test
    void processOrder_shouldThrowUnavailableExceptionWhenNetworkCommunicationFails() {

        UUID orderId =
                UUID.randomUUID();

        UUID correlationId =
                UUID.randomUUID();

        mockServer.expect(
                        requestTo(
                                BASE_URL
                                        + "/internal/v1/orders/"
                                        + orderId
                                        + "/process"
                        )
                )
                .andRespond(
                        withException(
                                new IOException(
                                        "Simulated network failure"
                                )
                        )
                );

        assertThrows(
                IntegrationServiceUnavailableException.class,
                () ->
                        integrationServiceClient.processOrder(
                                orderId,
                                correlationId,
                                processOrderRequest()
                        )
        );

        mockServer.verify();
    }

    @Test
    void processOrder_shouldNotTreat4xxAsServiceUnavailable() {

        UUID orderId =
                UUID.randomUUID();

        UUID correlationId =
                UUID.randomUUID();

        mockServer.expect(
                        requestTo(
                                BASE_URL
                                        + "/internal/v1/orders/"
                                        + orderId
                                        + "/process"
                        )
                )
                .andRespond(
                        withStatus(
                                HttpStatus.BAD_REQUEST
                        )
                );

        assertThrows(
                IllegalStateException.class,
                () ->
                        integrationServiceClient.processOrder(
                                orderId,
                                correlationId,
                                processOrderRequest()
                        )
        );

        mockServer.verify();
    }

    private ProcessOrderRequest processOrderRequest() {

        return new ProcessOrderRequest(
                "WEB-2026-000123",
                new CustomerRequest(
                        "CUST-10045",
                        "customer@example.com"
                ),
                List.of(
                        new OrderItemRequest(
                                "PROD-001",
                                2,
                                new BigDecimal("29.95")
                        )
                ),
                "EUR",
                new ShippingAddressRequest(
                        "123 Example Street",
                        null,
                        "Seville",
                        "41001",
                        "ES"
                ),
                Instant.parse(
                        "2026-09-08T09:00:00Z"
                )
        );
    }
}