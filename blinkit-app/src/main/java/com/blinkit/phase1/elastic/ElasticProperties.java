package com.blinkit.phase1.elastic;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.elastic")
public record ElasticProperties(
        String baseUrl,
        String index,
        int timeoutSeconds  // Timeout for ES operations (default in application.yml)
) {}
