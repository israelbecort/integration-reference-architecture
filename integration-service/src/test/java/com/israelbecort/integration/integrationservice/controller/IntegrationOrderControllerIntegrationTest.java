package com.israelbecort.integration.integrationservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IntegrationOrderControllerIntegrationTest {

    private static final String ORDER_ID =
            "b243423f-8047-49ea-b79f-50027400c022";

    private static final String CORRELATION_ID =
            "11111111-1111-4111-8111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAcceptValidOrderForProcessing() throws Exception {

        mockMvc.perform(
                        post("/internal/v1/orders/{orderId}/process", ORDER_ID)
                                .header("X-Correlation-Id", CORRELATION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest())
                )
                .andExpect(status().isAccepted())
                .andExpect(
                        header().string(
                                "X-Correlation-Id",
                                CORRELATION_ID
                        )
                )
                .andExpect(
                        jsonPath("$.orderId")
                                .value(ORDER_ID)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PROCESSING")
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(CORRELATION_ID)
                )
                .andExpect(
                        jsonPath("$.processingAcceptedAt")
                                .isNotEmpty()
                );
    }

    @Test
    void shouldReturnBadRequestWhenCorrelationIdIsMissing() throws Exception {

        mockMvc.perform(
                        post("/internal/v1/orders/{orderId}/process", ORDER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest())
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentType(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        header().doesNotExist("X-Correlation-Id")
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("INT-VALIDATION-003")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                );
    }

    @Test
    void shouldReturnBadRequestWhenOrderIdIsInvalid() throws Exception {

        mockMvc.perform(
                        post(
                                "/internal/v1/orders/{orderId}/process",
                                "invalid-order-id"
                        )
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest())
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentType(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        header().string(
                                "X-Correlation-Id",
                                CORRELATION_ID
                        )
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("INT-VALIDATION-004")
                );
    }

    @Test
    void shouldReturnBadRequestWhenCorrelationIdIsInvalid() throws Exception {

        mockMvc.perform(
                        post("/internal/v1/orders/{orderId}/process", ORDER_ID)
                                .header(
                                        "X-Correlation-Id",
                                        "invalid-correlation-id"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest())
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentType(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        header().doesNotExist("X-Correlation-Id")
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("INT-VALIDATION-004")
                );
    }

    @Test
    void shouldReturnBadRequestWhenQuantityIsInvalid() throws Exception {

        String request = """
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
                  },
                  "acceptedAt": "2026-09-07T10:00:00Z"
                }
                """;

        mockMvc.perform(
                        post("/internal/v1/orders/{orderId}/process", ORDER_ID)
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentType(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        header().string(
                                "X-Correlation-Id",
                                CORRELATION_ID
                        )
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("INT-VALIDATION-001")
                );
    }

    @Test
    void shouldReturnBadRequestWhenEmailIsInvalid() throws Exception {

        String request = """
                {
                  "externalOrderId": "WEB-2026-000123",
                  "customer": {
                    "customerId": "CUST-10045",
                    "email": "invalid-email"
                  },
                  "items": [
                    {
                      "productId": "PROD-001",
                      "quantity": 2,
                      "unitPrice": 29.95
                    }
                  ],
                  "currency": "EUR",
                  "shippingAddress": {
                    "addressLine1": "123 Example Street",
                    "city": "Seville",
                    "postalCode": "41001",
                    "country": "ES"
                  },
                  "acceptedAt": "2026-09-07T10:00:00Z"
                }
                """;

        mockMvc.perform(
                        post("/internal/v1/orders/{orderId}/process", ORDER_ID)
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentType(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("INT-VALIDATION-001")
                );
    }

    @Test
    void shouldReturnBadRequestWhenJsonIsMalformed() throws Exception {

        String malformedRequest = """
                {
                  "externalOrderId": "WEB-2026-000123",
                  "customer":
                }
                """;

        mockMvc.perform(
                        post("/internal/v1/orders/{orderId}/process", ORDER_ID)
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(malformedRequest)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentType(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        header().string(
                                "X-Correlation-Id",
                                CORRELATION_ID
                        )
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("INT-VALIDATION-002")
                );
    }

    @Test
    void shouldAcceptGuestCustomerWithoutCustomerId()
            throws Exception {

        String request = """
            {
              "externalOrderId": "WEB-GUEST-000001",
              "customer": {
                "email": "guest@example.com"
              },
              "items": [
                {
                  "productId": "PROD-001",
                  "quantity": 1,
                  "unitPrice": 29.95
                }
              ],
              "currency": "EUR",
              "shippingAddress": {
                "addressLine1": "123 Example Street",
                "city": "Seville",
                "postalCode": "41001",
                "country": "ES"
              },
              "acceptedAt": "2026-09-10T10:00:00Z"
            }
            """;

        mockMvc.perform(
                        post(
                                "/internal/v1/orders/{orderId}/process",
                                ORDER_ID
                        )
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldReturnBadRequestWhenCustomerIdIsBlank()
            throws Exception {

        String request = """
            {
              "externalOrderId": "WEB-GUEST-000002",
              "customer": {
                "customerId": "   ",
                "email": "guest@example.com"
              },
              "items": [
                {
                  "productId": "PROD-001",
                  "quantity": 1,
                  "unitPrice": 29.95
                }
              ],
              "currency": "EUR",
              "shippingAddress": {
                "addressLine1": "123 Example Street",
                "city": "Seville",
                "postalCode": "41001",
                "country": "ES"
              },
              "acceptedAt": "2026-09-10T10:00:00Z"
            }
            """;

        mockMvc.perform(
                        post(
                                "/internal/v1/orders/{orderId}/process",
                                ORDER_ID
                        )
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest());
    }

    private String validRequest() {

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
                    }
                  ],
                  "currency": "EUR",
                  "shippingAddress": {
                    "addressLine1": "123 Example Street",
                    "city": "Seville",
                    "postalCode": "41001",
                    "country": "ES"
                  },
                  "acceptedAt": "2026-09-07T10:00:00Z"
                }
                """;
    }
}