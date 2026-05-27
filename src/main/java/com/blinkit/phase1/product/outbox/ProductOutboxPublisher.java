package com.blinkit.phase1.product.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductOutboxPublisher {

    private final ProductOutboxRepository repo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${app.outbox.poll.fixed-delay-ms:1000}")
    public void publish() {

        var batch = repo.findNextNew(Integer.parseInt(System.getProperty("outbox.batchSize", "500")));

        for (var e : batch) {
            try {
                kafkaTemplate.send("product-events", e.getAggregateId().toString(), e.getPayload()).get();
                e.setStatus("PUBLISHED");
                e.setUpdatedAt(Instant.now());
                repo.save(e);
            } catch (Exception ex) {
                e.setAttempts(e.getAttempts() + 1);
                e.setUpdatedAt(Instant.now());
                if (e.getAttempts() >= 10) e.setStatus("FAILED");
                repo.save(e);

                log.error("Outbox publish failed for {}: {}", e.getId(), ex.getMessage());
            }
        }
    }
}
