package com.blinkit.phase1.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    /**
     * Find all orders for a specific user, ordered by creation date descending.
     */
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find all orders for a user with pagination.
     */
    Page<OrderEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Find order by ID with items eagerly fetched (avoids N+1).
     */
    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<OrderEntity> findByIdWithItems(@Param("id") UUID id);

    /**
     * Find all orders by status.
     */
    List<OrderEntity> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    /**
     * Count orders by status (useful for dashboards).
     */
    long countByStatus(OrderStatus status);
}
