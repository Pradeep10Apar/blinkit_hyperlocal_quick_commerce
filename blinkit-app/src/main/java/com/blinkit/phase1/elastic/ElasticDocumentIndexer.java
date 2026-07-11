package com.blinkit.phase1.elastic;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class ElasticDocumentIndexer {

    private final ElasticProperties props;

    private WebClient client() {
        return WebClient.builder().baseUrl(props.baseUrl()).build();
    }

    public void indexJsonDoc(String id, JsonNode doc) {
        client().put()
                .uri("/{index}/_doc/{id}", props.index(), id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(doc)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}

