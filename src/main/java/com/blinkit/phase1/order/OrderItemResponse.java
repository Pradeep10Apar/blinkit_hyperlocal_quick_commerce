package com.blinkit.phase1.order;

import java.util.UUID;

public record OrderItemResponse(
        UUID productId,
        String name,
        int quantity,
        double price
) {

    

}
