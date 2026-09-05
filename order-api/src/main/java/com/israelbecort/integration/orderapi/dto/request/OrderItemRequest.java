package com.israelbecort.integration.orderapi.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrderItemRequest(

        @NotBlank
        @Size(max = 100)
        String productId,

        @Positive
        int quantity,

        @DecimalMin(value = "0.01")
        BigDecimal unitPrice

) {
}