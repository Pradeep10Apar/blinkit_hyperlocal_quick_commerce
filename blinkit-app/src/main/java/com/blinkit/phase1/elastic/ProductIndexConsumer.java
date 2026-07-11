package com.blinkit.phase1.elastic;



import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductIndexConsumer {

    private final ElasticDocumentIndexer indexer;
    private final ObjectMapper mapper;

    @KafkaListener(topics = "product-events", groupId = "blinkit-phase1")
    public void onMessage(String payload) throws Exception {
        JsonNode doc = mapper.readTree(payload);
        String id = doc.get("id").asText();
        indexer.indexJsonDoc(id, doc);
    }
}

