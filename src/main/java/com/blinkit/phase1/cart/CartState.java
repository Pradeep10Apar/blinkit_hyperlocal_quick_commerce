package com.blinkit.phase1.cart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartState {
    private Map<UUID, Integer> items = new LinkedHashMap<>();
    private Instant updatedAt = Instant.now();
}
