package com.blinkit.phase1.cart;

import com.blinkit.phase1.cart.dto.AddCartItemRequest;
import com.blinkit.phase1.cart.dto.CartItemResponse;
import com.blinkit.phase1.cart.dto.CartResponse;
import com.blinkit.phase1.cart.dto.UpdateCartItemQuantityRequest;
import com.blinkit.phase1.product.ProductEntity;
import com.blinkit.phase1.product.ProductNotFoundException;
import com.blinkit.phase1.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private static final String CART_KEY_PREFIX = "cart:";

    private final ProductRepository productRepository;
    private final RedisTemplate<String, CartState> redisTemplate;

    @Value("${blinkit.cart.ttl-hours:168}")
    private long cartTtlHours;

    public CartResponse getCart(String cartId) {
        String normalizedCartId = normalizeCartId(cartId);
        CartState state = getState(normalizedCartId);
        return toResponse(normalizedCartId, state);
    }

    public CartResponse addItem(String cartId, AddCartItemRequest request) {
        String normalizedCartId = normalizeCartId(cartId);
        ProductEntity product = requireActiveProduct(request.productId());

        CartState state = getState(normalizedCartId);
        state.getItems().merge(product.getId(), request.quantity(), Integer::sum);
        state.setUpdatedAt(Instant.now());
        saveState(normalizedCartId, state);

        return toResponse(normalizedCartId, state);
    }

    public CartResponse updateItemQuantity(String cartId, UUID productId, UpdateCartItemQuantityRequest request) {
        String normalizedCartId = normalizeCartId(cartId);
        ProductEntity product = requireActiveProduct(productId);

        CartState state = getState(normalizedCartId);
        if (!state.getItems().containsKey(product.getId())) {
            throw new CartItemNotFoundException("Product not present in cart: " + productId);
        }

        state.getItems().put(product.getId(), request.quantity());
        state.setUpdatedAt(Instant.now());
        saveState(normalizedCartId, state);

        return toResponse(normalizedCartId, state);
    }

    public CartResponse removeItem(String cartId, UUID productId) {
        String normalizedCartId = normalizeCartId(cartId);
        CartState state = getState(normalizedCartId);
        state.getItems().remove(productId);
        state.setUpdatedAt(Instant.now());

        if (state.getItems().isEmpty()) {
            redisTemplate.delete(key(normalizedCartId));
            return emptyCart(normalizedCartId, state.getUpdatedAt());
        }

        saveState(normalizedCartId, state);
        return toResponse(normalizedCartId, state);
    }

    public CartResponse clearCart(String cartId) {
        String normalizedCartId = normalizeCartId(cartId);
        redisTemplate.delete(key(normalizedCartId));
        return emptyCart(normalizedCartId, Instant.now());
    }

    private String normalizeCartId(String cartId) {
        try {
            return UUID.fromString(cartId).toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("X-Cart-Id must be a valid UUID");
        }
    }

    private ProductEntity requireActiveProduct(UUID productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
        if (!product.isActive()) {
            throw new IllegalArgumentException("Product is inactive: " + productId);
        }
        return product;
    }

    private CartState getState(String cartId) {
        ValueOperations<String, CartState> ops = redisTemplate.opsForValue();
        CartState state = ops.get(key(cartId));
        if (state == null) {
            return new CartState(new LinkedHashMap<>(), Instant.now());
        }
        if (state.getItems() == null) {
            state.setItems(new LinkedHashMap<>());
        }
        if (state.getUpdatedAt() == null) {
            state.setUpdatedAt(Instant.now());
        }
        return state;
    }

    private void saveState(String cartId, CartState state) {
        redisTemplate.opsForValue().set(key(cartId), state, Duration.ofHours(cartTtlHours));
    }

    private CartResponse toResponse(String cartId, CartState state) {
        if (state.getItems().isEmpty()) {
            return emptyCart(cartId, state.getUpdatedAt());
        }

        Map<UUID, ProductEntity> productsById = productRepository.findAllById(state.getItems().keySet()).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalQuantity = 0;

        for (Map.Entry<UUID, Integer> entry : state.getItems().entrySet()) {
            ProductEntity product = productsById.get(entry.getKey());
            if (product == null) {
                continue;
            }

            int quantity = entry.getValue();
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(lineTotal);
            totalQuantity += quantity;

            items.add(new CartItemResponse(
                    product.getId(),
                    product.getName(),
                    product.getBrand(),
                    product.getCategory(),
                    product.getImageUrl(),
                    product.getPrice(),
                    quantity,
                    lineTotal,
                    product.isActive()
            ));
        }

        items.sort(Comparator.comparing(CartItemResponse::name, String.CASE_INSENSITIVE_ORDER));
        return new CartResponse(cartId, items, totalQuantity, subtotal, state.getUpdatedAt());
    }

    private CartResponse emptyCart(String cartId, Instant updatedAt) {
        return new CartResponse(cartId, List.of(), 0, BigDecimal.ZERO, updatedAt);
    }

    private String key(String cartId) {
        return CART_KEY_PREFIX + cartId;
    }
}
