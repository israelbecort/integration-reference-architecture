package com.israelbecort.integration.orderapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    private static final String ORDERS_URL = "/api/v1/orders";

    private static final String CORRELATION_ID =
            "b37166f4-8a39-4ffd-9599-c42ca48b83d0";

    private static final String IDEMPOTENCY_KEY =
            "9fe2d76a-268f-470b-a47f-a3895ab3a189";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createOrder_shouldReturn202WhenRequestIsValid() throws Exception {

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Correlation-Id", CORRELATION_ID)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .content(validOrderRequest())
                )
                .andExpect(status().isAccepted())
                .andExpect(header().string(
                        "X-Correlation-Id",
                        CORRELATION_ID
                ))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.externalOrderId")
                        .value("WEB-2026-000123"))
                .andExpect(jsonPath("$.status")
                        .value("ACCEPTED"))
                .andExpect(jsonPath("$.correlationId")
                        .value(CORRELATION_ID))
                .andExpect(jsonPath("$.acceptedAt").exists());
    }

    @Test
    void createOrder_shouldGenerateCorrelationIdWhenHeaderIsMissing()
            throws Exception {

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .content(validOrderRequest())
                )
                .andExpect(status().isAccepted())
                .andExpect(header().string(
                        "X-Correlation-Id",
                        matchesPattern(
                                "^[0-9a-fA-F-]{36}$"
                        )
                ))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void createOrder_shouldReturn400WhenQuantityIsInvalid()
            throws Exception {

        String invalidRequest = """
                {
                  "externalOrderId": "WEB-2026-000123",
                  "customer": {
                    "customerId": "CUST-10045",
                    "email": "customer@example.com"
                  },
                  "items": [
                    {
                      "productId": "PROD-001",
                      "quantity": 0,
                      "unitPrice": 29.95
                    }
                  ],
                  "currency": "EUR",
                  "shippingAddress": {
                    "addressLine1": "123 Example Street",
                    "city": "Seville",
                    "postalCode": "41001",
                    "country": "ES"
                  }
                }
                """;

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Correlation-Id", CORRELATION_ID)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .content(invalidRequest)
                )
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                        "X-Correlation-Id",
                        CORRELATION_ID
                ))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.type")
                        .value("https://example.com/problems/order-validation"))
                .andExpect(jsonPath("$.title")
                        .value("Order validation failed"))
                .andExpect(jsonPath("$.status")
                        .value(400))
                .andExpect(jsonPath("$.errorCode")
                        .value("ORD-VALIDATION-001"))
                .andExpect(jsonPath("$.correlationId")
                        .value(CORRELATION_ID));
    }

    @Test
    void createOrder_shouldReturn400WhenIdempotencyKeyIsMissing()
            throws Exception {

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Correlation-Id", CORRELATION_ID)
                                .content(validOrderRequest())
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status")
                        .value(400))
                .andExpect(jsonPath("$.errorCode")
                        .value("ORD-VALIDATION-003"));
    }

    @Test
    void createOrder_shouldReturn400WhenJsonIsMalformed()
            throws Exception {

        String malformedJson = """
                {
                  "externalOrderId": "WEB-2026-000123",
                }
                """;

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Correlation-Id", CORRELATION_ID)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .content(malformedJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status")
                        .value(400))
                .andExpect(jsonPath("$.errorCode")
                        .value("ORD-VALIDATION-002"));
    }

    private String validOrderRequest() {

        return """
                {
                  "externalOrderId": "WEB-2026-000123",
                  "customer": {
                    "customerId": "CUST-10045",
                    "email": "customer@example.com"
                  },
                  "items": [
                    {
                      "productId": "PROD-001",
                      "quantity": 2,
                      "unitPrice": 29.95
                    },
                    {
                      "productId": "PROD-002",
                      "quantity": 1,
                      "unitPrice": 15.50
                    }
                  ],
                  "currency": "EUR",
                  "shippingAddress": {
                    "addressLine1": "123 Example Street",
                    "city": "Seville",
                    "postalCode": "41001",
                    "country": "ES"
                  }
                }
                """;
    }
}