package com.israelbecort.integration.orderapi.service;

import com.israelbecort.integration.orderapi.dto.request.OrderItemRequest;
import com.israelbecort.integration.orderapi.dto.request.OrderRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class OrderRequestHashCalculator {

    public String calculate(OrderRequest request) {

        StringBuilder canonicalRequest = new StringBuilder();

        append(canonicalRequest, request.externalOrderId());

        append(canonicalRequest, request.customer().customerId());
        append(canonicalRequest, request.customer().email());

        append(canonicalRequest, request.currency());

        append(canonicalRequest, request.shippingAddress().addressLine1());
        append(canonicalRequest, request.shippingAddress().addressLine2());
        append(canonicalRequest, request.shippingAddress().city());
        append(canonicalRequest, request.shippingAddress().postalCode());
        append(canonicalRequest, request.shippingAddress().country());

        append(
                canonicalRequest,
                Integer.toString(request.items().size())
        );

        for (OrderItemRequest item : request.items()) {

            append(canonicalRequest, item.productId());

            append(
                    canonicalRequest,
                    Integer.toString(item.quantity())
            );

            append(
                    canonicalRequest,
                    normalizeDecimal(item.unitPrice())
            );
        }

        return sha256(canonicalRequest.toString());
    }

    private void append(
            StringBuilder builder,
            String value
    ) {

        String normalized =
                value == null
                        ? "<null>"
                        : value;

        builder
                .append(normalized.length())
                .append(':')
                .append(normalized)
                .append('|');
    }

    private String normalizeDecimal(BigDecimal value) {

        return value
                .stripTrailingZeros()
                .toPlainString();
    }

    private String sha256(String value) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is not available.",
                    exception
            );
        }
    }
}