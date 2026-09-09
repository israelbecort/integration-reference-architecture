package com.israelbecort.integration.orderapi.client.integration;

import com.israelbecort.integration.orderapi.client.integration.dto.ProcessOrderAcceptedResponse;
import com.israelbecort.integration.orderapi.client.integration.dto.ProcessOrderRequest;
import com.israelbecort.integration.orderapi.exception.IntegrationServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
public class IntegrationServiceClient {

    private final RestClient integrationServiceRestClient;

    public IntegrationServiceClient(
            RestClient integrationServiceRestClient
    ) {
        this.integrationServiceRestClient =
                integrationServiceRestClient;
    }

    public ProcessOrderAcceptedResponse processOrder(
            UUID orderId,
            UUID correlationId,
            ProcessOrderRequest request
    ) {

        ResponseEntity<ProcessOrderAcceptedResponse> response;

        try {

            response =
                    integrationServiceRestClient
                            .post()
                            .uri(
                                    "/internal/v1/orders/{orderId}/process",
                                    orderId
                            )
                            .header(
                                    "X-Correlation-Id",
                                    correlationId.toString()
                            )
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .toEntity(
                                    ProcessOrderAcceptedResponse.class
                            );

        } catch (ResourceAccessException exception) {

            throw new IntegrationServiceUnavailableException(
                    "The Integration Service is temporarily unavailable.",
                    exception
            );

        } catch (RestClientResponseException exception) {

            if (exception.getStatusCode().is5xxServerError()) {

                throw new IntegrationServiceUnavailableException(
                        "The Integration Service is temporarily unavailable.",
                        exception
                );
            }

            throw new IllegalStateException(
                    "The Integration Service rejected the internal request with status: "
                            + exception.getStatusCode(),
                    exception
            );
        }

        if (response.getStatusCode() != HttpStatus.ACCEPTED) {

            throw new IllegalStateException(
                    "Integration Service returned unexpected status: "
                            + response.getStatusCode()
            );
        }

        ProcessOrderAcceptedResponse responseBody =
                response.getBody();

        if (responseBody == null) {

            throw new IllegalStateException(
                    "Integration Service returned an empty response body."
            );
        }

        return responseBody;
    }
}