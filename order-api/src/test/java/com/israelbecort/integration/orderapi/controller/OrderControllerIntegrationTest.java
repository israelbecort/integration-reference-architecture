package com.israelbecort.integration.orderapi.controller;

import com.israelbecort.integration.orderapi.TestcontainersConfiguration;
import com.israelbecort.integration.orderapi.client.integration.IntegrationServiceClient;
import com.israelbecort.integration.orderapi.client.integration.dto.ProcessOrderAcceptedResponse;
import com.israelbecort.integration.orderapi.client.integration.dto.ProcessOrderRequest;
import com.israelbecort.integration.orderapi.domain.OrderStatus;
import com.israelbecort.integration.orderapi.persistence.entity.OrderEntity;
import com.israelbecort.integration.orderapi.persistence.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.israelbecort.integration.orderapi.exception.IntegrationServiceUnavailableException;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
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

    @MockitoBean
    private IntegrationServiceClient integrationServiceClient;

    @BeforeEach
    void setUp() {

        orderRepository.deleteAll();

        when(
                integrationServiceClient.processOrder(
                        any(UUID.class),
                        any(UUID.class),
                        any(ProcessOrderRequest.class)
                )
        ).thenAnswer(invocation -> {

            UUID orderId =
                    invocation.getArgument(0);

            UUID correlationId =
                    invocation.getArgument(1);

            return new ProcessOrderAcceptedResponse(
                    orderId,
                    "PROCESSING",
                    correlationId,
                    Instant.now()
            );
        });
    }

    @Test
    void createOrder_shouldReturn202WhenRequestIsValid()
            throws Exception {

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        IDEMPOTENCY_KEY
                                )
                                .content(validOrderRequest())
                )
                .andExpect(status().isAccepted())
                .andExpect(
                        header().string(
                                "X-Correlation-Id",
                                CORRELATION_ID
                        )
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.orderId")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.externalOrderId")
                                .value("WEB-2026-000123")
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
                        jsonPath("$.acceptedAt")
                                .exists()
                );

        assertEquals(
                1,
                orderRepository.count()
        );

        OrderEntity persistedOrder =
                orderRepository
                        .findByIdempotencyKey(
                                UUID.fromString(IDEMPOTENCY_KEY)
                        )
                        .orElseThrow();

        assertEquals(
                OrderStatus.PROCESSING,
                persistedOrder.getStatus()
        );
    }

    @Test
    void createOrder_shouldGenerateCorrelationIdWhenHeaderIsMissing()
            throws Exception {

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "Idempotency-Key",
                                        IDEMPOTENCY_KEY
                                )
                                .content(validOrderRequest())
                )
                .andExpect(status().isAccepted())
                .andExpect(
                        header().string(
                                "X-Correlation-Id",
                                matchesPattern(
                                        "^[0-9a-fA-F-]{36}$"
                                )
                        )
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PROCESSING")
                );

        assertEquals(
                1,
                orderRepository.count()
        );

        OrderEntity persistedOrder =
                orderRepository
                        .findByIdempotencyKey(
                                UUID.fromString(IDEMPOTENCY_KEY)
                        )
                        .orElseThrow();

        assertEquals(
                OrderStatus.PROCESSING,
                persistedOrder.getStatus()
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
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        IDEMPOTENCY_KEY
                                )
                                .content(invalidRequest)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        header().string(
                                "X-Correlation-Id",
                                CORRELATION_ID
                        )
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "https://example.com/problems/order-validation"
                                )
                )
                .andExpect(
                        jsonPath("$.title")
                                .value("Order validation failed")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("ORD-VALIDATION-001")
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(CORRELATION_ID)
                );

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
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .content(validOrderRequest())
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("ORD-VALIDATION-003")
                );

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
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        IDEMPOTENCY_KEY
                                )
                                .content(malformedJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("ORD-VALIDATION-002")
                );

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
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        IDEMPOTENCY_KEY
                                )
                                .content(validOrderRequest())
                )
                .andExpect(status().isAccepted())
                .andExpect(
                        jsonPath("$.status")
                                .value("PROCESSING")
                );

        OrderEntity persistedOrder =
                orderRepository
                        .findByIdempotencyKey(
                                UUID.fromString(IDEMPOTENCY_KEY)
                        )
                        .orElseThrow();

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        IDEMPOTENCY_KEY
                                )
                                .content(validOrderRequest())
                )
                .andExpect(status().isAccepted())
                .andExpect(
                        jsonPath("$.orderId")
                                .value(
                                        persistedOrder
                                                .getOrderId()
                                                .toString()
                                )
                )
                .andExpect(
                        jsonPath("$.acceptedAt")
                                .value(
                                        persistedOrder
                                                .getAcceptedAt()
                                                .toString()
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PROCESSING")
                );

        assertEquals(
                1,
                orderRepository.count()
        );

        verify(
                integrationServiceClient,
                times(1)
        ).processOrder(
                any(UUID.class),
                any(UUID.class),
                any(ProcessOrderRequest.class)
        );
    }

    @Test
    void createOrder_shouldReturn409WhenIdempotencyKeyIsReusedWithDifferentRequest()
            throws Exception {

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        IDEMPOTENCY_KEY
                                )
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
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        IDEMPOTENCY_KEY
                                )
                                .content(differentRequest)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("ORD-CONFLICT-001")
                );

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
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        IDEMPOTENCY_KEY
                                )
                                .content(validOrderRequest())
                )
                .andExpect(status().isAccepted());

        String anotherIdempotencyKey =
                "8ec03111-4bfc-4d52-a226-506741701d13";

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        anotherIdempotencyKey
                                )
                                .content(validOrderRequest())
                )
                .andExpect(status().isConflict())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("ORD-CONFLICT-001")
                );

        assertEquals(
                1,
                orderRepository.count()
        );
    }

    @Test
    void createOrder_shouldRetryAcceptedOrderWhenIntegrationServiceRecovers()
            throws Exception {

        when(
                integrationServiceClient.processOrder(
                        any(UUID.class),
                        any(UUID.class),
                        any(ProcessOrderRequest.class)
                )
        )
                .thenThrow(
                        new IntegrationServiceUnavailableException(
                                "The Integration Service is temporarily unavailable."
                        )
                )
                .thenAnswer(invocation -> {

                    UUID orderId =
                            invocation.getArgument(0);

                    UUID correlationId =
                            invocation.getArgument(1);

                    return new ProcessOrderAcceptedResponse(
                            orderId,
                            "PROCESSING",
                            correlationId,
                            Instant.now()
                    );
                });

        /*
         * First attempt:
         *
         * Order is persisted successfully,
         * but Integration Service is unavailable.
         */
        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        IDEMPOTENCY_KEY
                                )
                                .content(validOrderRequest())
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(
                        header().string(
                                "X-Correlation-Id",
                                CORRELATION_ID
                        )
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(503)
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("ORD-DEPENDENCY-001")
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(CORRELATION_ID)
                );

        /*
         * The database transaction must already
         * have committed the order as ACCEPTED.
         */
        OrderEntity acceptedOrder =
                orderRepository
                        .findByIdempotencyKey(
                                UUID.fromString(IDEMPOTENCY_KEY)
                        )
                        .orElseThrow();

        assertEquals(
                OrderStatus.ACCEPTED,
                acceptedOrder.getStatus()
        );

        UUID originalOrderId =
                acceptedOrder.getOrderId();

        Instant originalAcceptedAt =
                acceptedOrder.getAcceptedAt();

        assertEquals(
                1,
                orderRepository.count()
        );

        /*
         * Second attempt:
         *
         * Same Idempotency-Key + same request.
         * Integration Service has recovered.
         */
        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        IDEMPOTENCY_KEY
                                )
                                .content(validOrderRequest())
                )
                .andExpect(status().isAccepted())
                .andExpect(
                        jsonPath("$.orderId")
                                .value(originalOrderId.toString())
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PROCESSING")
                )
                .andExpect(
                        jsonPath("$.acceptedAt")
                                .value(originalAcceptedAt.toString())
                );

        OrderEntity processingOrder =
                orderRepository
                        .findByIdempotencyKey(
                                UUID.fromString(IDEMPOTENCY_KEY)
                        )
                        .orElseThrow();

        assertEquals(
                originalOrderId,
                processingOrder.getOrderId()
        );

        assertEquals(
                originalAcceptedAt,
                processingOrder.getAcceptedAt()
        );

        assertEquals(
                OrderStatus.PROCESSING,
                processingOrder.getStatus()
        );

        assertEquals(
                1,
                orderRepository.count()
        );

        verify(
                integrationServiceClient,
                times(2)
        ).processOrder(
                any(UUID.class),
                any(UUID.class),
                any(ProcessOrderRequest.class)
        );
    }
    @Test
    void createOrder_shouldAcceptGuestCustomerWithoutCustomerId()
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
              }
            }
            """;

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .content(request)
                )
                .andExpect(status().isAccepted());
    }

    @Test
    void createOrder_shouldReturn400WhenCustomerIdIsBlank()
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
              }
            }
            """;

        mockMvc.perform(
                        post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        "X-Correlation-Id",
                                        CORRELATION_ID
                                )
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .content(request)
                )
                .andExpect(status().isBadRequest());
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