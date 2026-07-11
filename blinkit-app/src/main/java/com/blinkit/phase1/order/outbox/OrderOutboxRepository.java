package com.blinkit.phase1.order.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEvent, UUID> {

    @Query(value = """
      select * from order_outbox
      where status = 'NEW'
      order by created_at asc
      limit ?1
      """, nativeQuery = true)
    List<OrderOutboxEvent> findNextNew(int limit);
}
