package com.israelbecort.integration.orderapi.exception;

public class IntegrationServiceUnavailableException
        extends RuntimeException {

    public IntegrationServiceUnavailableException(
            String message
    ) {
        super(message);
    }

    public IntegrationServiceUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}