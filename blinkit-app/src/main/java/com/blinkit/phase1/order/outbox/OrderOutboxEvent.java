package com.blinkit.phase1.order.outbox;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_outbox")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderOutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;   // e.g. ORDER_PLACED

    @Column(nullable = false)
    private String payload;

    @Column(nullable = false, length = 20)
    private String status;      // NEW, PUBLISHED, FAILED

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
