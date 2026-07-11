package com.blinkit.inventory.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

import com.blinkit.inventory.api.model.FailedItem;
import com.blinkit.inventory.api.model.ReserveItem;
import com.blinkit.inventory.api.model.ReserveStockRequest;
import com.blinkit.inventory.api.model.ReserveStockResponse;
import com.blinkit.inventory.api.model.ReservedItem;
import com.blinkit.inventory.api.model.FailedItem.ReasonEnum;
import com.blinkit.inventory.dto.InventoryResponse;
import com.blinkit.inventory.entity.Inventory;
import com.blinkit.inventory.repository.InventoryRepository;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;


    public List<InventoryResponse> checkInventory(List<UUID> productIds){
        
        log.info("Checking inventory for {} product(s): {}", productIds.size(), productIds);

        var inventoryList = inventoryRepository.findAllByProductIdIn(productIds);
        log.info("Found {} inventory record(s) in DB", inventoryList.size());
        
        if (inventoryList.isEmpty()) {
            log.warn("No inventory records found for productIds: {}", productIds);
        } else {
            inventoryList.forEach(inv -> 
                log.debug("Inventory record: productId={}, quantity={}", inv.getProductId(), inv.getQuantity())
            );
        }

        List<InventoryResponse> response = inventoryList.stream()
                .map(inventory -> 
                    InventoryResponse.builder()
                        .productId(inventory.getId())
                        .isInStock(inventory.getQuantity() > 0)
                        .build()
                ).toList();
        
        log.info("Returning {} inventory response(s): {}", response.size(), response);
        
        return response;
    }



    @Transactional
    public ReserveStockResponse reserveStock(ReserveStockRequest request) {
        log.info("Reserving stock for {} items", request.getItems().size());
        
        List<ReservedItem> reservedItems = new ArrayList<>();
        List<FailedItem> failedItems = new ArrayList<>();
        List<Inventory> validInventories = new ArrayList<>();
        List<Integer> quantitiesToReserve = new ArrayList<>();
        
        // Single pass: validate all items and prepare reservations
        for (ReserveItem item : request.getItems()) {
            Optional<Inventory> inventoryOpt = inventoryRepository.findByProductId(item.getProductId());
            
            if (inventoryOpt.isEmpty()) {
                log.warn("Product {} not found in inventory", item.getProductId());
                failedItems.add(new FailedItem()
                    .productId(item.getProductId())
                    .requestedQuantity(item.getQuantity())
                    .availableQuantity(0)
                    .reason(ReasonEnum.PRODUCT_NOT_FOUND));
                continue;
            }
            
            Inventory inventory = inventoryOpt.get();
            int available = inventory.getQuantity() - inventory.getReserved();
            
            if (item.getQuantity() > available) {
                log.warn("Insufficient stock for product {}: requested={}, available={}", 
                    item.getProductId(), item.getQuantity(), available);
                failedItems.add(new FailedItem()
                    .productId(item.getProductId())
                    .requestedQuantity(item.getQuantity())
                    .availableQuantity(available)
                    .reason(ReasonEnum.INSUFFICIENT_STOCK));
            } else {
                // Track for later reservation (don't modify entity yet)
                validInventories.add(inventory);
                quantitiesToReserve.add(item.getQuantity());
                reservedItems.add(new ReservedItem()
                    .productId(item.getProductId())
                    .reservedQuantity(item.getQuantity()));
            }
        }
        
        // Happy path: failedItems is empty AND all requested items are reserved
        if (failedItems.isEmpty() && reservedItems.size() == request.getItems().size()) {
            // Now actually apply reservations to DB
            for (int i = 0; i < validInventories.size(); i++) {
                Inventory inv = validInventories.get(i);
                inv.setReserved(inv.getReserved() + quantitiesToReserve.get(i));
            }
            inventoryRepository.saveAll(validInventories);
            
            log.info("Successfully reserved {} items", reservedItems.size());
            return new ReserveStockResponse()
                .success(true)
                .reservedItems(reservedItems)
                .failedItems(List.of());
        }
        
        // Failure path: ask user to modify cart
        log.info("Reservation failed: {} items could not be reserved", failedItems.size());
        return new ReserveStockResponse()
            .success(false)
            .reservedItems(List.of())
            .failedItems(failedItems);
    }


}
