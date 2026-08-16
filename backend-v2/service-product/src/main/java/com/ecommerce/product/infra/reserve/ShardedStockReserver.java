package com.ecommerce.product.infra.reserve;

import com.ecommerce.product.domain.model.StockContention;
import com.ecommerce.product.domain.model.StockShard;
import com.ecommerce.product.domain.repository.StockShardRepository;
import com.ecommerce.product.domain.service.StockReserver;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link StockContention#POPULAR} — 재고를 N샤드로 쪼개고 임의 샤드를 조건부 UPDATE로 깎는다.
 *
 * <p>차감 문장은 {@link AtomicStockReserver}와 같은 모양이고 대상만 샤드다. 요청이 샤드에
 * 고르게 흩어지므로 단일 row 직렬화가 1/N로 낮아진다. 재고 1개당 row를 만드는
 * {@link UnitStockReserver}와 달리 row 수가 재고량이 아니라 샤드 수에 비례해, 재고가
 * 많아도 테이블이 커지지 않는다.
 *
 * <p><b>이 방식의 값은 폴백에 있다.</b> 잔량이 적어질수록 고른 샤드가 비어 있을 확률이
 * 올라가는데, 그때 그냥 실패시키면 총 잔량이 남았는데도 못 파는 재고가 생긴다. 그래서
 * 빈 샤드를 만나면 잔량이 가장 많은 샤드를 잠그고 거기서 가져온다. 폴백은 정렬과 잠금이
 * 붙어 정상 경로보다 비싸므로, 샤드 수는 "폴백이 드물게 일어날 만큼"으로 잡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShardedStockReserver implements StockReserver {

    private final StockShardRepository stockShardRepository;

    /**
     * 샤드 수.
     *
     * <p>크게 잡을수록 경합은 줄지만 샤드당 잔량이 작아져 폴백이 잦아진다. 16은 동시
     * 요청 수십 건 규모에서 경합을 충분히 낮추면서 샤드당 잔량을 남기는 값이다.
     */
    @Value("${stock.shard.count:16}")
    private int shardCount;

    @Override
    public StockContention contention() {
        return StockContention.POPULAR;
    }

    @Override
    public boolean reserve(Long variantId, Long orderId, int quantity) {
        int shardNo = ThreadLocalRandom.current().nextInt(shardCount);
        if (stockShardRepository.decrease(variantId, shardNo, quantity) > 0) {
            return true;
        }

        // 고른 샤드가 비었다. 총 잔량이 남아 있을 수 있으므로 가장 많은 샤드에서 가져온다.
        StockShard richest = stockShardRepository.lockRichestShard(variantId, quantity);
        if (richest == null) {
            return false;
        }
        return stockShardRepository.decrease(variantId, richest.getShardNo(), quantity) > 0;
    }

    @Override
    public void release(Long variantId, Long orderId, int quantity) {
        // 어느 샤드로 돌려도 총량은 같다. 임의 샤드로 돌려 반납이 한 곳에 몰리지 않게 한다.
        stockShardRepository.increase(variantId, ThreadLocalRandom.current().nextInt(shardCount), quantity);
    }

    @Override
    public void confirm(Long variantId, Long orderId, int quantity) {
        // 차감이 이미 끝나 있다.
    }
}
