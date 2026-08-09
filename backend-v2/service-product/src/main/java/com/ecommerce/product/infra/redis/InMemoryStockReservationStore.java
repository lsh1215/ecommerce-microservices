package com.ecommerce.product.infra.redis;

import com.ecommerce.product.application.service.StockReservationStore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemoryStockReservationStore implements StockReservationStore {

    private final Map<Long, Map<Long, Integer>> reservations = new ConcurrentHashMap<>();
    private final Map<Long, Long> available = new ConcurrentHashMap<>();

    @Override
    public boolean reserve(Long variantId, Long orderId, int quantity, int availableStock) {
        Map<Long, Integer> variantReservations =
                reservations.computeIfAbsent(variantId, ignored -> new ConcurrentHashMap<>());
        synchronized (variantReservations) {
            if (variantReservations.containsKey(orderId)) {
                return true;
            }
            int reservedTotal = variantReservations.values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            if (reservedTotal + quantity > availableStock) {
                return false;
            }
            variantReservations.put(orderId, quantity);
            return true;
        }
    }

    @Override
    public void release(Long variantId, Long orderId) {
        Map<Long, Integer> variantReservations = reservations.get(variantId);
        if (variantReservations != null) {
            variantReservations.remove(orderId);
        }
    }

    @Override
    public Optional<Integer> findReservedQuantity(Long variantId, Long orderId) {
        Map<Long, Integer> variantReservations = reservations.get(variantId);
        if (variantReservations == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(variantReservations.get(orderId));
    }

    @Override
    public void preloadAvailable(Long variantId, long availableStock) {
        available.put(variantId, availableStock);
    }

    @Override
    public int reserveRedisOnly(Long variantId, Long orderId, int quantity) {
        Long availableStock = available.get(variantId);
        if (availableStock == null) {
            return -1;
        }
        Map<Long, Integer> variantReservations =
                reservations.computeIfAbsent(variantId, ignored -> new ConcurrentHashMap<>());
        synchronized (variantReservations) {
            if (variantReservations.containsKey(orderId)) {
                return 2;
            }
            int reservedTotal = variantReservations.values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            if (reservedTotal + quantity > availableStock) {
                return 0;
            }
            variantReservations.put(orderId, quantity);
            return 1;
        }
    }
}
