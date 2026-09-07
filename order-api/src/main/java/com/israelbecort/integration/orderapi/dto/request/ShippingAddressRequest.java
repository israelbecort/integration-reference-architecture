package com.israelbecort.integration.orderapi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(

        @NotBlank
        @Size(max = 200)
        String addressLine1,

        @Size(max = 200)
        String addressLine2,

        @NotBlank
        @Size(max = 100)
        String city,

        @NotBlank
        @Size(max = 20)
        String postalCode,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{2}$")
        String country

) {
}