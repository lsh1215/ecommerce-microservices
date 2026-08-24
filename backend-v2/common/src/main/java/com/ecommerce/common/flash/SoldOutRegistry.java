package com.ecommerce.common.flash;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 매진된 상품을 파드 메모리에 들고 있는다.
 *
 * <p>여기 있는 값은 <b>권위가 아니라 힌트</b>다. 재고 판정은 계속 DB 가 한다. 이 플래그가
 * 늦게 서거나 파드마다 다르게 서 있어도 틀린 결과가 나오지 않고, 그동안 몇 건이 더
 * 접수될 뿐이다. 그래서 공유 저장소 없이 파드 로컬로 둔다.
 *
 * <p>플래그를 세우는 데 Redis 를 쓰지 않는 이유가 이것이다. 정확성이 걸려 있지 않으므로
 * 정확한 공유가 필요 없고, Kafka 신호를 각 파드가 받아 로컬에 적어두면 충분하다.
 */
@Component
public class SoldOutRegistry {

    private final Map<Long, Boolean> soldOut = new ConcurrentHashMap<>();

    public boolean isSoldOut(long variantId) {
        return soldOut.containsKey(variantId);
    }

    /** 처음 세우는 것이면 true. 매진 신호를 한 번만 발행하기 위해 반환값을 쓴다. */
    public boolean markSoldOut(long variantId) {
        return soldOut.putIfAbsent(variantId, Boolean.TRUE) == null;
    }

    /** 재입고나 새 발매 시작. 신호를 받으면 푼다. */
    public void clear(long variantId) {
        soldOut.remove(variantId);
    }
}
