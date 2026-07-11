package com.blinkit.phase1.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient with load balancing support.
 * 
 * The @LoadBalanced annotation enables the WebClient to resolve
 * service names (like "INVENTORY-SERVICE") to actual URLs
 * using Eureka service discovery.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
