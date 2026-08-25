package com.ecommerce.product.application.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.ProductErrorCode;
import com.ecommerce.product.domain.model.StockUnitHolder;
import com.ecommerce.product.domain.model.StockUnitStatus;
import com.ecommerce.product.domain.repository.StockUnitRepository;
import com.ecommerce.product.infra.reserve.UnitStockReserver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선착순 재고 확보.
 *
 * <p>큐도 드레이너도 폴링도 인메모리 캐시도 없다. 확보는 {@code SELECT ... FOR UPDATE SKIP LOCKED}로
 * AVAILABLE 유닛을 그 자리에서 집어 RESERVED로 바꾸고 즉시 응답한다. 존재하는 유닛 row 수가 재고
 * 상한이므로 오버셀이 구조적으로 불가능하다. 상태는 전부 DB에 있어 어느 인스턴스가 받아도 같다.
 *
 * <p><b>주체는 주문이 아니라 접수 메시지의 offset이다.</b> 이 시점에는 주문이 없다. 접수는
 * Kafka 발행만 하고 DB에 아무것도 쓰지 않으며, 확보에 성공한 사람만 나중에 주문으로 넘어간다.
 * 그래서 유닛의 주인을 {@link StockUnitHolder#FLASH} + offset으로 기록한다. 주문 id와 같은
 * 컬럼에 종류 없이 섞으면 두 번호 공간이 겹칠 때 서로의 유닛을 건드린다.
 */
@Service
@RequiredArgsConstructor
public class FlashReserveService {

    private static final List<StockUnitStatus> HELD = List.of(
            StockUnitStatus.RESERVED, StockUnitStatus.CONFIRMED);

    private final StockUnitRepository stockUnitRepository;
    private final UnitStockReserver unitStockReserver;

    /**
     * 확보. AVAILABLE 유닛을 quantity개 집어 RESERVED로 확정한다.
     *
     * @param offset 접수 메시지의 파티션 offset. 같은 상품은 같은 파티션이라 이 값이 유일하고,
     *               재전송돼도 같은 값이라 이중 확보가 없다.
     * @return {@code true}면 확보(GRANTED), {@code false}면 재고 부족(SOLD_OUT).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean reserve(long offset, Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK,
                    "Reserve quantity must be positive");
        }

        // 멱등성: 이 offset 이 이미 확보한 유닛은 다시 집지 않는다. 재전송으로 다시 들어와도
        // 여기서 true 로 빠져나가고, granter 가 결과를 다시 발행해 통보가 복구된다.
        long already = stockUnitRepository.countByHolderTypeAndHolderIdAndVariantIdAndStatusIn(
                StockUnitHolder.FLASH, offset, variantId, HELD);
        if (already >= quantity) {
            return true;
        }
        int need = (int) (quantity - already);

        // 유닛을 집는 방법 자체는 HOT 등급과 같다(SKIP LOCKED로 서로 다른 row 확보).
        // 그 로직은 UnitStockReserver 한 곳에 두고, 여기서는 이 흐름에만 있는 멱등성
        // 처리(위의 already 계산)만 담당한다.
        return unitStockReserver.reserve(StockUnitHolder.FLASH, offset, variantId, need);
    }

    /** 결제 성공: 이 offset 이 쥔 RESERVED 유닛을 CONFIRMED(영구 소진)로 확정한다. */
    @Transactional
    public boolean confirm(long offset, Long variantId) {
        return unitStockReserver.confirm(StockUnitHolder.FLASH, offset, variantId);
    }

    /** 결제 실패·보상: 이 offset 이 쥔 RESERVED 유닛을 AVAILABLE로 되돌린다(풀 반납). */
    @Transactional
    public boolean release(long offset, Long variantId) {
        return unitStockReserver.release(StockUnitHolder.FLASH, offset, variantId);
    }
}
