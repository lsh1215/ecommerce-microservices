package com.ecommerce.order.application.dto;

public record StockReservation(Long orderId, Long variantId, int quantity) {}
