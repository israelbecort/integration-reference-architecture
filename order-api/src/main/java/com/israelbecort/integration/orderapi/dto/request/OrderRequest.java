package com.israelbecort.integration.orderapi.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderRequest(

        @NotBlank
        @Size(max = 100)
        String externalOrderId,

        @NotNull
        @Valid
        CustomerRequest customer,

        @NotEmpty
        @Valid
        List<OrderItemRequest> items,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String currency,

        @NotNull
        @Valid
        ShippingAddressRequest shippingAddress

) {
}