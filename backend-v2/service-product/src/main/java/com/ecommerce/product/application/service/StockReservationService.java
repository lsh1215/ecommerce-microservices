package com.ecommerce.product.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.model.StockContention;
import com.ecommerce.product.domain.model.StockReservation;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import com.ecommerce.product.domain.repository.StockReservationRepository;
import com.ecommerce.product.domain.service.StockReserver;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 예약 진입점. 옵션의 경합 등급에 맞는 {@link StockReserver}를 골라 위임한다.
 *
 * <p>호출부는 등급을 모르고 알 필요도 없다. 등급은 운영 중 관측된 경합에 따라 바뀌는
 * 값이고, 바뀌어도 이 위층은 그대로다.
 *
 * <p>예약 이력({@code stock_reservation})은 등급과 무관하게 남긴다. 재고를 어디서 깎았든
 * "누가 무엇을 얼마나 잡았는지"는 취소·확정·만료 회수에 필요하고, 중복 예약을 막는
 * 지점이기도 하다.
 */
@Slf4j
@Service
public class StockReservationService {

    private final Map<StockContention, StockReserver> reservers = new EnumMap<>(StockContention.class);
    private final ProductVariantRepository productVariantRepository;
    private final StockReservationRepository stockReservationRepository;

    public StockReservationService(List<StockReserver> reserverBeans,
                                   ProductVariantRepository productVariantRepository,
                                   StockReservationRepository stockReservationRepository) {
        reserverBeans.forEach(r -> reservers.put(r.contention(), r));
        this.productVariantRepository = productVariantRepository;
        this.stockReservationRepository = stockReservationRepository;
        // 등급이 늘었는데 구현을 빠뜨리면 런타임 첫 요청에서야 드러난다. 기동 시 막는다.
        for (StockContention c : StockContention.values()) {
            if (!reservers.containsKey(c)) {
                throw new IllegalStateException("StockReserver 구현 없음: " + c);
            }
        }
    }

    @Transactional
    public void reserve(Long variantId, Long orderId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    "Reserve quantity must be positive");
        }
        // 같은 주문이 같은 옵션을 다시 요청한 경우. 재차감하면 재고가 이중으로 줄기
        // 때문에 다시 깎지 않는다. 다만 조용히 통과시키면 안 되는 두 경우가 있다.
        var existing = stockReservationRepository.findByOrderIdAndVariantId(orderId, variantId);
        if (existing.isPresent()) {
            StockReservation reservation = existing.get();
            if (reservation.isReleased()) {
                // 이미 반납된 예약이다. 재고는 돌아가 있으므로 이 요청을 성공으로
                // 처리하면 확보하지 않은 재고를 확보했다고 답하는 것이 된다.
                throw new BusinessException(ProductErrorCode.RESERVATION_NOT_FOUND);
            }
            if (reservation.getQuantity() != quantity) {
                // 같은 주문이 다른 수량으로 다시 왔다. 어느 쪽이 맞는지 이 자리에서
                // 알 수 없으므로 조용히 무시하지 않고 호출부로 돌려보낸다.
                throw new BusinessException(ProductErrorCode.INVALID_VARIANT_OPERATION,
                        "Reservation quantity does not match existing reservation");
            }
            return;
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));

        if (!reserverFor(variant).reserve(variantId, orderId, quantity)) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    String.format("Requested %d but not available", quantity));
        }
        stockReservationRepository.save(StockReservation.reserve(orderId, variantId, quantity));
    }

    @Transactional
    public void release(Long variantId, Long orderId, int quantity) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        reserverFor(variant).release(variantId, orderId, quantity);
    }

    @Transactional
    public void confirm(Long variantId, Long orderId, int quantity) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.VARIANT_NOT_FOUND));
        reserverFor(variant).confirm(variantId, orderId, quantity);
    }

    private StockReserver reserverFor(ProductVariant variant) {
        return reservers.get(variant.getStockContention());
    }
}
