package com.blinkit.phase1.cart;

import com.blinkit.phase1.cart.dto.AddCartItemRequest;
import com.blinkit.phase1.cart.dto.CartResponse;
import com.blinkit.phase1.cart.dto.UpdateCartItemQuantityRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private static final String CART_ID_HEADER = "X-Cart-Id";

    private final CartService cartService;

    @GetMapping
    public CartResponse getCart(@RequestHeader(CART_ID_HEADER) String cartId) {
        return cartService.getCart(cartId);
    }

    @PostMapping("/items")
    public CartResponse addItem(
            @RequestHeader(CART_ID_HEADER) String cartId,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        return cartService.addItem(cartId, request);
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateItemQuantity(
            @RequestHeader(CART_ID_HEADER) String cartId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request
    ) {
        return cartService.updateItemQuantity(cartId, productId, request);
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(
            @RequestHeader(CART_ID_HEADER) String cartId,
            @PathVariable UUID productId
    ) {
        return cartService.removeItem(cartId, productId);
    }

    @DeleteMapping
    public CartResponse clearCart(@RequestHeader(CART_ID_HEADER) String cartId) {
        return cartService.clearCart(cartId);
    }
}
