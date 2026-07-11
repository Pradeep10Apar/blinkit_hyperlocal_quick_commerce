package com.blinkit.inventory.controller;

import java.util.List;
import java.util.UUID;

import org.apache.kafka.common.message.ProduceRequestData;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.blinkit.inventory.api.model.ReserveStockRequest;
import com.blinkit.inventory.api.model.ReserveStockResponse;
import com.blinkit.inventory.dto.InventoryResponse;
import com.blinkit.inventory.service.InventoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;


    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<InventoryResponse> checkInventory(@RequestParam List<UUID> productIds){

        return inventoryService.checkInventory(productIds);

        
    }

    @PostMapping("/reserve-stock")
    @ResponseStatus(HttpStatus.OK)
    public ReserveStockResponse reserveStock (@RequestBody @Valid ReserveStockRequest reserveStockRequest){
        return inventoryService.reserveStock(reserveStockRequest);
    }

}
