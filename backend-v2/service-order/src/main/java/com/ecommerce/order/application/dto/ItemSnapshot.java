package com.ecommerce.order.application.dto;

/**
 * Pairs a {@link ProductSnapshotDto} (fetched from service-product before
 * the DB transaction opens) with the requested quantity, so the persistence
 * layer can build {@code OrderItem} entities without re-issuing RestClient
 * calls inside its narrow transactional scope.
 */
public record ItemSnapshot(
        ProductSnapshotDto snapshot,
        int quantity
) {}
