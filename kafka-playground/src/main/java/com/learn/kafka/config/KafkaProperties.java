package com.learn.kafka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           CONFIG-DRIVEN TOPIC PROPERTIES                    ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Binds the YAML config under "app.kafka" into Java objects. ║
 * ║                                                             ║
 * ║  application.yml:                                           ║
 * ║    app:                                                     ║
 * ║      kafka:                                                 ║
 * ║        topics:                                              ║
 * ║          greetings:              ← map key                  ║
 * ║            name: greetings       ← TopicDef.name            ║
 * ║            partitions: 3         ← TopicDef.partitions      ║
 * ║            replicas: 1           ← TopicDef.replicas        ║
 * ║                                                             ║
 * ║  In Java:                                                   ║
 * ║    properties.getTopics().get("greetings").getName()        ║
 * ║    → returns "greetings"                                    ║
 * ║                                                             ║
 * ║  To add a new topic: add it in YAML, that's it!             ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProperties {

    /**
     * Map of topic key → topic definition.
     * The map key (e.g., "greetings") is just a label for your reference.
     * The actual Kafka topic name comes from TopicDef.name.
     */
    private Map<String, TopicDef> topics = new HashMap<>();

    /**
     * Represents one Kafka topic's configuration.
     */
    @Data
    public static class TopicDef {
        private String name;
        private int partitions = 1;    // default 1 if not specified
        private int replicas = 1;      // default 1 if not specified
    }

    // ── Convenience getters for commonly used topic names ──

    /** Get the actual Kafka topic name by its config key */
    public String topicName(String key) {
        TopicDef def = topics.get(key);
        if (def == null) {
            throw new IllegalArgumentException("No topic configured for key: " + key
                    + ". Available keys: " + topics.keySet());
        }
        return def.getName();
    }
}
