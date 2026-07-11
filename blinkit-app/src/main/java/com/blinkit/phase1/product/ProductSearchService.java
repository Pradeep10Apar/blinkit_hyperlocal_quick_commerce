package com.blinkit.phase1.product;

import com.blinkit.phase1.product.dto.ProductResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;


@Service
public class ProductSearchService {

    private static final String INDEX = "products_v1";

    private final RestClient es;
    private final ObjectMapper mapper;


    public ProductSearchService(RestClient elasticRestClient, ObjectMapper mapper) {
        this.es = elasticRestClient;
        this.mapper = mapper;
    }

    public List<ProductResponse> search(String q, int page, int size) {
        if (q == null || q.isBlank()) {
            return List.of();
        }

        int from = Math.max(page, 0) * Math.max(size, 1);

        // ES Query: multi_match across name/brand/category with boosts + fuzziness
        String body = """
            {
              "from": %d,
              "size": %d,
              "query": {
                "bool": {
                  "filter": [
                    { "term": { "active": true } }
                  ],
                  "must": [
                    {
                      "multi_match": {
                        "query": %s,
                        "fields": ["name^3", "brand^2", "category"],
                        "fuzziness": "AUTO"
                      }
                    }
                  ]
                }
              }
            }
            """.formatted(from, size, mapper.valueToTree(q).toString()); // safely JSON-escape q

        // Call ES: POST /products_v1/_search
        String responseJson = es.post()
                .uri("/" + INDEX + "/_search")
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        return parseHits(responseJson);
    }

    private List<ProductResponse> parseHits(String responseJson) {
        try {
            JsonNode root = mapper.readTree(responseJson);
            JsonNode hits = root.path("hits").path("hits");

            List<ProductResponse> out = new ArrayList<>();
            for (JsonNode h : hits) {
                String id = h.path("_id").asText(null);
                JsonNode src = h.path("_source");

                // Map ES _source -> your API DTO
                ProductResponse pr = new ProductResponse(
                        UUID.fromString(id),
                        src.path("name").asText(null),
                        src.path("brand").asText(null),
                        src.path("category").asText(null),
                        src.path("price").isNumber() ? src.path("price").decimalValue() : null,
                        src.path("active").asBoolean(true),
                        src.path("imageUrl").isTextual() ? src.path("imageUrl").asText(null) : null,
                        src.path("createdAt").isTextual() ? java.time.Instant.parse(src.path("createdAt").asText()) : null,
                        src.path("updatedAt").isTextual() ? java.time.Instant.parse(src.path("updatedAt").asText()) : null
                );

                out.add(pr);
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Elasticsearch response", e);
        }
    }
}
