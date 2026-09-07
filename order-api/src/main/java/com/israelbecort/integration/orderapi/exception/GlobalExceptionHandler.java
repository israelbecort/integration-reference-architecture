package com.israelbecort.integration.orderapi.exception;

import com.israelbecort.integration.orderapi.dto.response.ProblemDetailsResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailsResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {

        UUID correlationId = resolveCorrelationId(request);

        ProblemDetailsResponse problem = buildProblem(
                "https://example.com/problems/order-validation",
                "Order validation failed",
                HttpStatus.BAD_REQUEST,
                "One or more fields are invalid.",
                request.getRequestURI(),
                "ORD-VALIDATION-001",
                correlationId
        );

        return buildResponse(problem, HttpStatus.BAD_REQUEST, correlationId);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetailsResponse> handleMalformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {

        UUID correlationId = resolveCorrelationId(request);

        ProblemDetailsResponse problem = buildProblem(
                "https://example.com/problems/order-validation",
                "Order validation failed",
                HttpStatus.BAD_REQUEST,
                "The request body is malformed or contains invalid data.",
                request.getRequestURI(),
                "ORD-VALIDATION-002",
                correlationId
        );

        return buildResponse(problem, HttpStatus.BAD_REQUEST, correlationId);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetailsResponse> handleMissingHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {

        UUID correlationId = resolveCorrelationId(request);

        ProblemDetailsResponse problem = buildProblem(
                "https://example.com/problems/order-validation",
                "Required header missing",
                HttpStatus.BAD_REQUEST,
                "Required header '" + exception.getHeaderName() + "' is missing.",
                request.getRequestURI(),
                "ORD-VALIDATION-003",
                correlationId
        );

        return buildResponse(problem, HttpStatus.BAD_REQUEST, correlationId);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetailsResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {

        UUID correlationId = resolveCorrelationId(request);

        ProblemDetailsResponse problem = buildProblem(
                "https://example.com/problems/order-validation",
                "Invalid request value",
                HttpStatus.BAD_REQUEST,
                "One or more request values have an invalid format.",
                request.getRequestURI(),
                "ORD-VALIDATION-004",
                correlationId
        );

        return buildResponse(problem, HttpStatus.BAD_REQUEST, correlationId);
    }

    @ExceptionHandler(OrderConflictException.class)
    public ResponseEntity<ProblemDetailsResponse> handleOrderConflict(
            OrderConflictException exception,
            HttpServletRequest request
    ) {

        UUID correlationId = resolveCorrelationId(request);

        ProblemDetailsResponse problem = buildProblem(
                "https://example.com/problems/order-conflict",
                "Order conflict",
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                "ORD-CONFLICT-001",
                correlationId
        );

        return buildResponse(
                problem,
                HttpStatus.CONFLICT,
                correlationId
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetailsResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {

        UUID correlationId = resolveCorrelationId(request);

        ProblemDetailsResponse problem = buildProblem(
                "https://example.com/problems/order-conflict",
                "Order conflict",
                HttpStatus.CONFLICT,
                "The request conflicts with an existing order or idempotency constraint.",
                request.getRequestURI(),
                "ORD-CONFLICT-001",
                correlationId
        );

        return buildResponse(
                problem,
                HttpStatus.CONFLICT,
                correlationId
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailsResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {

        UUID correlationId = resolveCorrelationId(request);

        ProblemDetailsResponse problem = buildProblem(
                "https://example.com/problems/internal-error",
                "Internal server error",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected technical error occurred.",
                request.getRequestURI(),
                "ORD-INTERNAL-001",
                correlationId
        );

        return buildResponse(
                problem,
                HttpStatus.INTERNAL_SERVER_ERROR,
                correlationId
        );
    }

    private ProblemDetailsResponse buildProblem(
            String type,
            String title,
            HttpStatus status,
            String detail,
            String instance,
            String errorCode,
            UUID correlationId
    ) {

        return new ProblemDetailsResponse(
                type,
                title,
                status.value(),
                detail,
                instance,
                errorCode,
                correlationId,
                Instant.now()
        );
    }

    private ResponseEntity<ProblemDetailsResponse> buildResponse(
            ProblemDetailsResponse problem,
            HttpStatus status,
            UUID correlationId
    ) {

        HttpHeaders headers = new HttpHeaders();

        headers.set(
                CORRELATION_HEADER,
                correlationId.toString()
        );

        headers.setContentType(
                MediaType.APPLICATION_PROBLEM_JSON
        );

        return new ResponseEntity<>(
                problem,
                headers,
                status
        );
    }

    private UUID resolveCorrelationId(HttpServletRequest request) {

        String correlationId =
                request.getHeader(CORRELATION_HEADER);

        if (correlationId != null && !correlationId.isBlank()) {
            try {
                return UUID.fromString(correlationId);
            } catch (IllegalArgumentException ignored) {
                // A new correlation identifier is generated below.
            }
        }

        return UUID.randomUUID();
    }
}