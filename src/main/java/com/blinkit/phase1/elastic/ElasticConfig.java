package com.blinkit.phase1.elastic;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;


@Configuration
@EnableConfigurationProperties(ElasticProperties.class)
public class ElasticConfig {

    @Bean
    public RestClient elasticRestClient(@Value("${app.elasticsearch.url}") String elasticUrl) {
        return RestClient.builder()
                .baseUrl(elasticUrl)   // e.g. http://localhost:9200
                .build();
    }


}
