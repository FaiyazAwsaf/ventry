package com.ventry.booking.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * @LoadBalanced marks this builder so Spring Cloud LoadBalancer resolves service-name
     * URIs like "http://event-service" via Eureka - the same mechanism the Gateway's own
     * lb:// routes use. Without this, "event-service" isn't a real hostname and every call
     * fails to resolve.
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
