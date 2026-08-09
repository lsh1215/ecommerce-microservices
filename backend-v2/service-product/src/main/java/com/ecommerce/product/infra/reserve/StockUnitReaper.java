package com.ecommerce.product.infra.reserve;

import com.ecommerce.product.domain.repository.StockUnitRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 언더셀(재고가 남았는데 못 파는 것) 방지용 reaper.
 *
 * <p>결제 성공 confirm 없이 TTL을 넘긴 RESERVED 유닛을 AVAILABLE로 되돌려 다시 팔 수 있게 한다.
 * cutoff를 DB 시계로 계산하므로 커넥터 타임존과 무관하게 정확하다.
 *
 * <p>TTL은 결제 제한 시간보다 넉넉히 크게 잡아야 한다. 그렇지 않으면 결제 성공 직전에 유닛이 회수돼
 * confirm이 아무 유닛도 못 찾을 수 있다. {@code flash.reserve.mode=unit}일 때만 등록된다.
 */
@Component
@Slf4j
@Profile("!test")
@ConditionalOnProperty(name = "flash.reserve.mode", havingValue = "unit")
public class StockUnitReaper {

    private final StockUnitRepository stockUnitRepository;
    private final long ttlSeconds;
    private final Counter reapedCounter;

    public StockUnitReaper(StockUnitRepository stockUnitRepository,
                           MeterRegistry meterRegistry,
                           @Value("${flash.reserve.reaper.ttl-seconds:600}") long ttlSeconds) {
        this.stockUnitRepository = stockUnitRepository;
        this.ttlSeconds = Math.max(1, ttlSeconds);
        this.reapedCounter = Counter.builder("flash_reserve_reaped_total")
                .description("TTL 만료로 AVAILABLE로 회수된 RESERVED 유닛 수").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${flash.reserve.reaper.fixed-delay-ms:5000}")
    @Transactional
    public void reap() {
        int reaped = stockUnitRepository.releaseStaleReserved(ttlSeconds);
        if (reaped > 0) {
            reapedCounter.increment(reaped);
            log.info("만료된 RESERVED 유닛 {}건을 AVAILABLE로 회수(ttl={}s)", reaped, ttlSeconds);
        }
    }
}
