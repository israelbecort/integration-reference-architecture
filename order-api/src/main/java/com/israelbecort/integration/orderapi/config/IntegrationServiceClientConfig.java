package com.israelbecort.integration.orderapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class IntegrationServiceClientConfig {

    @Bean
    RestClient integrationServiceRestClient(
            RestClient.Builder builder,
            @Value("${integration-service.base-url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                .build();
    }
}