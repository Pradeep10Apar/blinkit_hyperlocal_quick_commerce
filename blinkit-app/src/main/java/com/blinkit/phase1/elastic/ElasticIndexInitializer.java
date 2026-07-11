package com.blinkit.phase1.elastic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ensures the Elasticsearch index exists on startup.
 * For Phase 1 we keep mapping simple and rely on ES dynamic mapping.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticIndexInitializer {

    private final ElasticIndexService elasticIndexService;
    private final ElasticProperties props;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("Ensuring Elasticsearch index '{}' exists...", props.index());
        elasticIndexService.ensureIndexExists(props.index());
    }
}
