package com.israelbecort.integration.integrationservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(

        @NotBlank
        @Size(max = 100)
        String customerId,

        @NotBlank
        @Email
        @Size(max = 254)
        String email

) {
}