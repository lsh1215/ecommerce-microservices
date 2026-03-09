package com.ecommerce.inventory.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.inventory.api.dto.request.AdjustStockRequest;
import com.ecommerce.inventory.api.dto.response.InventoryEventResponse;
import com.ecommerce.inventory.api.dto.response.InventoryResponse;
import com.ecommerce.inventory.application.service.InventoryService;
import com.ecommerce.inventory.domain.model.Inventory;
import com.ecommerce.inventory.domain.model.InventoryEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/variants/{variantId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getByVariantId(@PathVariable Long variantId) {
        Inventory inventory = inventoryService.findByVariantId(variantId);
        return ResponseEntity.ok(ApiResponse.success(InventoryResponse.from(inventory)));
    }

    @PutMapping("/variants/{variantId}/adjust")
    public ResponseEntity<ApiResponse<InventoryResponse>> adjust(
            @PathVariable Long variantId,
            @Valid @RequestBody AdjustStockRequest request) {
        Inventory inventory = inventoryService.adjust(variantId, request.quantityChange(), request.reason());
        return ResponseEntity.ok(ApiResponse.success(InventoryResponse.from(inventory)));
    }

    @GetMapping("/{inventoryId}/events")
    public ResponseEntity<ApiResponse<List<InventoryEventResponse>>> getEvents(@PathVariable Long inventoryId) {
        List<InventoryEvent> events = inventoryService.findEvents(inventoryId);
        List<InventoryEventResponse> response = events.stream()
                .map(InventoryEventResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
