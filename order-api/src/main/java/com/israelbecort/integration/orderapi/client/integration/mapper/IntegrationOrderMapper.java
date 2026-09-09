package com.israelbecort.integration.orderapi.client.integration.mapper;

import com.israelbecort.integration.orderapi.client.integration.dto.CustomerRequest;
import com.israelbecort.integration.orderapi.client.integration.dto.OrderItemRequest;
import com.israelbecort.integration.orderapi.client.integration.dto.ProcessOrderRequest;
import com.israelbecort.integration.orderapi.client.integration.dto.ShippingAddressRequest;
import com.israelbecort.integration.orderapi.dto.request.OrderRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class IntegrationOrderMapper {

    public ProcessOrderRequest toProcessOrderRequest(
            OrderRequest orderRequest,
            Instant acceptedAt
    ) {

        CustomerRequest customer =
                new CustomerRequest(
                        orderRequest.customer().customerId(),
                        orderRequest.customer().email()
                );

        List<OrderItemRequest> items =
                orderRequest.items()
                        .stream()
                        .map(item ->
                                new OrderItemRequest(
                                        item.productId(),
                                        item.quantity(),
                                        item.unitPrice()
                                )
                        )
                        .toList();

        ShippingAddressRequest shippingAddress =
                new ShippingAddressRequest(
                        orderRequest.shippingAddress().addressLine1(),
                        orderRequest.shippingAddress().addressLine2(),
                        orderRequest.shippingAddress().city(),
                        orderRequest.shippingAddress().postalCode(),
                        orderRequest.shippingAddress().country()
                );

        return new ProcessOrderRequest(
                orderRequest.externalOrderId(),
                customer,
                items,
                orderRequest.currency(),
                shippingAddress,
                acceptedAt
        );
    }
}