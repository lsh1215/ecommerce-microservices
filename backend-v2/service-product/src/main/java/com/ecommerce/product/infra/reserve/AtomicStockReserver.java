package com.ecommerce.product.infra.reserve;

import com.ecommerce.product.domain.model.StockContention;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import com.ecommerce.product.domain.service.StockReserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link StockContention#NORMAL} — 재고 row 하나를 조건부 UPDATE로 깎는다.
 *
 * <pre>{@code
 * UPDATE product_variant SET stock_quantity = stock_quantity - ?
 * WHERE id = ? AND stock_quantity >= ?
 * }</pre>
 *
 * <p>읽고-빼고-쓰는 세 단계가 한 문장 안에 있으므로 그 사이에 다른 트랜잭션이 끼어들 수
 * 없다. 비관적 락으로 row를 잡고 애플리케이션에서 계산한 뒤 쓰는 방식과 결과는 같지만,
 * 락을 잡고 있는 구간이 UPDATE 실행 시간으로 줄어든다.
 *
 * <p>직렬화 지점은 여전히 그 row 하나다. 평시에는 주문이 수천 개 옵션에 흩어져 옵션당
 * 초당 몇 건이므로 문제가 되지 않고, 요청당 DB 작업이 세 등급 중 가장 적다. 단일 옵션에
 * 주문이 몰리기 시작하면 이 row가 병목이 되고, 그때 등급을 올린다.
 */
@Component
@RequiredArgsConstructor
public class AtomicStockReserver implements StockReserver {

    private final ProductVariantRepository productVariantRepository;

    @Override
    public StockContention contention() {
        return StockContention.NORMAL;
    }

    @Override
    public boolean reserve(Long variantId, Long orderId, int quantity) {
        return productVariantRepository.decreaseStock(variantId, quantity) > 0;
    }

    @Override
    public void release(Long variantId, Long orderId, int quantity) {
        productVariantRepository.increaseStock(variantId, quantity);
    }

    @Override
    public void confirm(Long variantId, Long orderId, int quantity) {
        // 차감이 이미 끝나 있다. 확정 시점에 재고 쪽에서 더 할 일이 없다.
    }
}
