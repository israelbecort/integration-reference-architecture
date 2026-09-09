package com.israelbecort.integration.integrationservice.exception;

import com.israelbecort.integration.integrationservice.dto.response.ProblemDetailsResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailsResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "https://example.com/problems/integration-validation",
                "Integration request validation failed",
                "One or more request fields are invalid.",
                "INT-VALIDATION-001",
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetailsResponse> handleMalformedRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "https://example.com/problems/integration-validation",
                "Integration request validation failed",
                "The request body is malformed or contains invalid data.",
                "INT-VALIDATION-002",
                request
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetailsResponse> handleMissingRequestHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "https://example.com/problems/integration-validation",
                "Required header missing",
                "Required header '" + exception.getHeaderName() + "' is missing.",
                "INT-VALIDATION-003",
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetailsResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "https://example.com/problems/integration-validation",
                "Invalid request value",
                "One or more request identifiers have an invalid format.",
                "INT-VALIDATION-004",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailsResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "https://example.com/problems/integration-internal-error",
                "Internal integration error",
                "An unexpected technical error occurred.",
                "INT-INTERNAL-001",
                request
        );
    }

    private ResponseEntity<ProblemDetailsResponse> buildResponse(
            HttpStatus status,
            String type,
            String title,
            String detail,
            String errorCode,
            HttpServletRequest request
    ) {
        UUID correlationId = resolveCorrelationId(request);

        ProblemDetailsResponse problemDetails =
                new ProblemDetailsResponse(
                        type,
                        title,
                        status.value(),
                        detail,
                        request.getRequestURI(),
                        errorCode,
                        correlationId,
                        Instant.now()
                );

        ResponseEntity.BodyBuilder responseBuilder =
                ResponseEntity
                        .status(status)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

        if (correlationId != null) {
            responseBuilder.header(
                    "X-Correlation-Id",
                    correlationId.toString()
            );
        }

        return responseBuilder.body(problemDetails);
    }

    private UUID resolveCorrelationId(HttpServletRequest request) {

        String correlationId =
                request.getHeader("X-Correlation-Id");

        if (correlationId == null || correlationId.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(correlationId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}