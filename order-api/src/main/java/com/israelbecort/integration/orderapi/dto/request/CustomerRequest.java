package com.israelbecort.integration.orderapi.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(

        @Size(max = 100)
        @Pattern(regexp = ".*\\S.*")
        String customerId,

        @NotBlank
        @Email
        @Size(max = 254)
        String email

) {
}