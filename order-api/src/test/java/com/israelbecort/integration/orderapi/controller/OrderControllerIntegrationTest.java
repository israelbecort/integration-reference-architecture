package com.israelbecort.integration.orderapi.controller;

import com.israelbecort.integration.orderapi.persistence.entity.OrderEntity;
import com.israelbecort.integration.orderapi.persistence.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void cleanDatabase() {
        orderRepository.deleteAll();
    }

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

        assertEquals(
                1,
                orderRepository.count()
        );
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

        assertEquals(
                1,
                orderRepository.count()
        );
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

        assertEquals(
                0,
                orderRepository.count()
        );
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

        assertEquals(
                0,
                orderRepository.count()
        );
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

        assertEquals(
                0,
                orderRepository.count()
        );
    }

    @Test
    void createOrder_shouldReturnSameOrderWhenIdempotencyKeyIsReused()
            throws Exception {

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Correlation-Id", CORRELATION_ID)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .content(validOrderRequest())
                )
                .andExpect(status().isAccepted());

        OrderEntity persistedOrder =
                orderRepository
                        .findByIdempotencyKey(
                                UUID.fromString(IDEMPOTENCY_KEY)
                        )
                        .orElseThrow();

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Correlation-Id", CORRELATION_ID)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .content(validOrderRequest())
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId")
                        .value(
                                persistedOrder
                                        .getOrderId()
                                        .toString()
                        ))
                .andExpect(jsonPath("$.acceptedAt")
                        .value(
                                persistedOrder
                                        .getAcceptedAt()
                                        .toString()
                        ));

        assertEquals(
                1,
                orderRepository.count()
        );
    }

    @Test
    void createOrder_shouldReturn409WhenIdempotencyKeyIsReusedWithDifferentRequest()
            throws Exception {

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Correlation-Id", CORRELATION_ID)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .content(validOrderRequest())
                )
                .andExpect(status().isAccepted());

        String differentRequest =
                validOrderRequest()
                        .replace(
                                "WEB-2026-000123",
                                "WEB-2026-000999"
                        );

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Correlation-Id", CORRELATION_ID)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .content(differentRequest)
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status")
                        .value(409))
                .andExpect(jsonPath("$.errorCode")
                        .value("ORD-CONFLICT-001"));

        assertEquals(
                1,
                orderRepository.count()
        );
    }

    @Test
    void createOrder_shouldReturn409WhenExternalOrderIdAlreadyExists()
            throws Exception {

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Correlation-Id", CORRELATION_ID)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .content(validOrderRequest())
                )
                .andExpect(status().isAccepted());

        String anotherIdempotencyKey =
                "8ec03111-4bfc-4d52-a226-506741701d13";

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Correlation-Id", CORRELATION_ID)
                                .header(
                                        "Idempotency-Key",
                                        anotherIdempotencyKey
                                )
                                .content(validOrderRequest())
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status")
                        .value(409))
                .andExpect(jsonPath("$.errorCode")
                        .value("ORD-CONFLICT-001"));

        assertEquals(
                1,
                orderRepository.count()
        );
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