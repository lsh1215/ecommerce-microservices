package com.ecommerce.product.infra.reserve;

import com.ecommerce.product.domain.model.StockContention;
import com.ecommerce.product.domain.model.StockUnitHolder;
import com.ecommerce.product.domain.repository.StockUnitRepository;
import com.ecommerce.product.domain.service.StockReserver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link StockContention#HOT} — 재고 1개당 row를 두고 {@code SKIP LOCKED}로 집는다.
 *
 * <pre>{@code
 * SELECT id FROM stock_unit
 * WHERE variant_id = ? AND status = 'AVAILABLE'
 * ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
 * }</pre>
 *
 * <p>동시 요청이 서로 다른 row를 잡으므로 경합이 사실상 사라진다. {@code SKIP LOCKED}가
 * 없으면 모든 요청이 "가장 앞선 AVAILABLE row" 하나를 잠그려 하고 나머지가 거기서
 * 블로킹되므로, row를 아무리 쪼개도 경합이 한 지점으로 다시 모인다.
 *
 * <p>요청당 DB 작업이 세 등급 중 가장 많다 — 잠금 스캔, row UPDATE, 그리고 {@code status}와
 * {@code holder_id}가 걸린 보조 인덱스 두 개의 엔트리 이동. 단일 옵션에 트래픽이 몰려 아래
 * 등급으로 직렬화가 풀리지 않을 때만 이 비용이 값을 한다.
 */
@Component
@RequiredArgsConstructor
public class UnitStockReserver implements StockReserver {

    private final StockUnitRepository stockUnitRepository;

    @Override
    public StockContention contention() {
        return StockContention.HOT;
    }

    @Override
    public boolean reserve(Long variantId, Long orderId, int quantity) {
        return reserve(StockUnitHolder.ORDER, orderId, variantId, quantity);
    }

    @Override
    public void release(Long variantId, Long orderId, int quantity) {
        release(StockUnitHolder.ORDER, orderId, variantId);
    }

    @Override
    public void confirm(Long variantId, Long orderId, int quantity) {
        confirm(StockUnitHolder.ORDER, orderId, variantId);
    }

    /**
     * 주체를 명시해서 확보한다.
     *
     * <p>{@link StockReserver} 인터페이스는 주문만 다루므로 선착순 경로가 쓸 수 없다.
     * 선착순은 주문이 아직 없고 접수 메시지의 offset 이 주체다.
     */
    public boolean reserve(StockUnitHolder holderType, Long holderId, Long variantId, int quantity) {
        List<Long> ids = stockUnitRepository.lockAvailableUnits(variantId, quantity);
        if (ids.size() < quantity) {
            // 필요한 만큼 못 집었다. 부분 확보는 하지 않는다 - 남은 유닛을 잡아두면
            // 뒤따르는 요청이 쓸 수 있는 재고를 줄이면서 이 주문도 완성되지 않는다.
            return false;
        }
        return stockUnitRepository.reserveUnits(ids, holderType.name(), holderId) == ids.size();
    }

    public boolean release(StockUnitHolder holderType, Long holderId, Long variantId) {
        return stockUnitRepository.release(holderType.name(), holderId, variantId) > 0;
    }

    public boolean confirm(StockUnitHolder holderType, Long holderId, Long variantId) {
        return stockUnitRepository.confirm(holderType.name(), holderId, variantId) > 0;
    }
}
