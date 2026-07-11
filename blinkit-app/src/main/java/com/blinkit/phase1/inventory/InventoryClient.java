package com.blinkit.phase1.inventory;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.blinkit.inventory.api.model.ReserveItem;
import com.blinkit.inventory.api.model.ReserveStockRequest;
import com.blinkit.inventory.api.model.ReserveStockResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Synchronous HTTP client for Inventory Service.
 * Uses WebClient in blocking mode for synchronous order flow.
 */
@Slf4j
@Component
public class InventoryClient {

    private final WebClient webClient;
    private final Duration timeout;

    public InventoryClient(
            WebClient.Builder webClientBuilder,
            @Value("${inventory.service.url:http://INVENTORY-SERVICE}") String inventoryServiceUrl,
            @Value("${inventory.service.timeout:5s}") Duration timeout) {
        
        this.webClient = webClientBuilder
                .baseUrl(inventoryServiceUrl)
                .build();
        this.timeout = timeout;
        
        log.info("InventoryClient initialized with baseUrl={}, timeout={}", inventoryServiceUrl, timeout);
    }

    /**
     * Reserves stock for a list of products (synchronous call).
     * 
     * @param items List of products and quantities to reserve
     * @return ReserveStockResponse with success/failure details
     * @throws InventoryServiceException if the call fails
     */
    public ReserveStockResponse reserveStock(List<ReserveItem> items) {
        log.info("Calling inventory service to reserve {} items", items.size());
        
        ReserveStockRequest request = new ReserveStockRequest().items(items);
        
        try {
            ReserveStockResponse response = webClient.post()
                    .uri("/api/inventory/reserve-stock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ReserveStockResponse.class)
                    .block(timeout);  // Blocking for synchronous flow
            
            if (response == null) {
                throw new InventoryServiceException("Received null response from inventory service");
            }
            
            log.info("Inventory reservation result: success={}, reserved={}, failed={}", 
                    response.getSuccess(), 
                    response.getReservedItems() != null ? response.getReservedItems().size() : 0,
                    response.getFailedItems() != null ? response.getFailedItems().size() : 0);
            
            return response;
            
        } catch (WebClientResponseException e) {
            log.error("Inventory service returned error: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new InventoryServiceException("Inventory service error: " + e.getMessage(), e);
            
        } catch (Exception e) {
            log.error("Failed to call inventory service: {}", e.getMessage(), e);
            throw new InventoryServiceException("Failed to communicate with inventory service", e);
        }
    }

    /**
     * Helper method to create a ReserveItem.
     */
    public static ReserveItem createReserveItem(UUID productId, int quantity) {
        return new ReserveItem()
                .productId(productId)
                .quantity(quantity);
    }
}
