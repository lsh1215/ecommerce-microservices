package com.ecommerce.product.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.domain.model.StockUnitStatus;
import com.ecommerce.product.domain.repository.StockUnitRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선착순 재고 예약 서비스 (Shopify식 · 동기 · stateless).
 *
 * <p>큐도 드레이너도 폴링도 인메모리 캐시도 없다. 예약은 {@code SELECT ... FOR UPDATE SKIP LOCKED}로
 * AVAILABLE 유닛을 그 자리에서 집어 RESERVED로 바꾸고 즉시 응답한다. 존재하는 유닛 row 수가 재고
 * 상한이므로 오버셀이 구조적으로 불가능하다. 상태는 전부 DB에 있어 어느 인스턴스가 받아도 같다.
 */
@Service
@RequiredArgsConstructor
public class FlashReserveService {

    private static final List<StockUnitStatus> HELD = List.of(
            StockUnitStatus.RESERVED, StockUnitStatus.CONFIRMED);

    private final StockUnitRepository stockUnitRepository;

    /**
     * 동기 예약. AVAILABLE 유닛을 quantity개 집어 RESERVED로 확정한다.
     *
     * @return {@code true}면 확보(GRANTED), {@code false}면 재고 부족(SOLD_OUT).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean reserve(Long orderId, Long variantId, int quantity) {
        if (orderId == null) {
            throw new BusinessException(ProductErrorCode.INVALID_VARIANT_OPERATION,
                    "orderId is required for reservation");
        }
        if (quantity <= 0) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    "Reserve quantity must be positive");
        }

        // 멱등성: 같은 주문이 이미 확보한 유닛은 다시 집지 않는다.
        long already = stockUnitRepository.countByOrderIdAndVariantIdAndStatusIn(orderId, variantId, HELD);
        if (already >= quantity) {
            return true;
        }
        int need = (int) (quantity - already);

        List<Long> ids = stockUnitRepository.lockAvailableUnits(variantId, need);
        if (ids.size() < need) {
            return false; // 재고 부족 — 부분 확보 없이 거절(잠근 row는 커밋 시 해제)
        }
        stockUnitRepository.reserveUnits(ids, orderId);
        return true;
    }

    /** 결제 성공: 이 주문의 RESERVED 유닛을 CONFIRMED(영구 소진)로 확정한다. */
    @Transactional
    public boolean confirm(Long orderId, Long variantId) {
        return stockUnitRepository.confirm(orderId, variantId) > 0;
    }

    /** 결제 실패·보상: 이 주문의 RESERVED 유닛을 AVAILABLE로 되돌린다(풀 반납). */
    @Transactional
    public boolean release(Long orderId, Long variantId) {
        return stockUnitRepository.release(orderId, variantId) > 0;
    }
}
