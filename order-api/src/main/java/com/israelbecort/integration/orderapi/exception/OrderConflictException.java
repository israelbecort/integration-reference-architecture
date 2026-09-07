package com.israelbecort.integration.orderapi.exception;

public class OrderConflictException extends RuntimeException {

    public OrderConflictException(String message) {
        super(message);
    }
}