package com.learn.kafka.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.ArrayList;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║            DYNAMIC TOPIC CREATION FROM CONFIG               ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  BEFORE (hardcoded):                                        ║
 * ║    @Bean NewTopic greetingsTopic() { ... }                  ║
 * ║    @Bean NewTopic ordersTopic() { ... }                     ║
 * ║    → Adding a topic = writing new Java code + recompile     ║
 * ║                                                             ║
 * ║  AFTER (config-driven):                                     ║
 * ║    Reads ALL topics from application.yml                    ║
 * ║    → Adding a topic = add 3 lines in YAML + restart         ║
 * ║    → No Java code changes ever!                             ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaTopicConfig {

    private final KafkaProperties kafkaProperties;

    /**
     * Dynamically creates ALL topics defined in application.yml.
     *
     * Spring's KafkaAdmin detects NewTopic beans and creates them
     * on the broker if they don't already exist.
     *
     * This single method replaces N separate @Bean methods!
     */
    @Bean
    public KafkaAdmin.NewTopics kafkaTopics() {
        List<NewTopic> topicList = new ArrayList<>();

        kafkaProperties.getTopics().forEach((key, def) -> {
            NewTopic topic = TopicBuilder.name(def.getName())
                    .partitions(def.getPartitions())
                    .replicas(def.getReplicas())
                    .build();
            topicList.add(topic);
            log.info("📋 Registered topic: '{}' (partitions={}, replicas={})",
                    def.getName(), def.getPartitions(), def.getReplicas());
        });

        return new KafkaAdmin.NewTopics(topicList.toArray(new NewTopic[0]));
    }
}
