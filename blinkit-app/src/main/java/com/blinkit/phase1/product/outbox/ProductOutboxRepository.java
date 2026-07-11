package com.blinkit.phase1.product.outbox;



import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface ProductOutboxRepository extends JpaRepository<ProductOutboxEvent, UUID> {

    @Query(value = """
      select * from product_outbox
      where status = 'NEW'
      order by created_at asc
      limit ?1
      """, nativeQuery = true)
    List<ProductOutboxEvent> findNextNew(int limit);
}

