package com.ecommerce.drop.api.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.drop.api.dto.request.AddDropProductRequest;
import com.ecommerce.drop.api.dto.request.CreateDropEventRequest;
import com.ecommerce.drop.api.dto.response.DropEventResponse;
import com.ecommerce.drop.api.dto.response.DropProductResponse;
import com.ecommerce.drop.application.service.DropEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DropEventController {

    private final DropEventService dropEventService;

    @PostMapping("/api/drops")
    public ResponseEntity<ApiResponse<DropEventResponse>> createDropEvent(
            @Valid @RequestBody CreateDropEventRequest request) {
        DropEventResponse response = dropEventService.createDropEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/drops/{publicId}")
    public ResponseEntity<ApiResponse<DropEventResponse>> getDropEvent(@PathVariable String publicId) {
        DropEventResponse response = dropEventService.getDropEvent(publicId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/drops")
    public ResponseEntity<ApiResponse<PageResponse<DropEventResponse>>> listDropEvents(Pageable pageable) {
        PageResponse<DropEventResponse> response = dropEventService.listDropEvents(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/api/drops/{publicId}/status")
    public ResponseEntity<ApiResponse<DropEventResponse>> transitionStatus(
            @PathVariable String publicId,
            @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        DropEventResponse response = dropEventService.transitionStatus(publicId, newStatus);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/api/drops/{publicId}/products")
    public ResponseEntity<ApiResponse<DropProductResponse>> addDropProduct(
            @PathVariable String publicId,
            @Valid @RequestBody AddDropProductRequest request) {
        DropProductResponse response = dropEventService.addDropProduct(publicId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @DeleteMapping("/api/drop-products/{publicId}")
    public ResponseEntity<ApiResponse<Void>> removeDropProduct(@PathVariable String publicId) {
        dropEventService.removeDropProduct(publicId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
