package com.ecommerce.product.api.controller;

import com.ecommerce.common.config.KafkaTopics;
import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.common.flash.SoldOutRegistry;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발매를 다시 여는 운영 조작.
 *
 * <p>매진 플래그는 각 파드의 메모리에 있고, 재고를 다시 채워도 저절로 풀리지 않는다. 유닛
 * row 를 채운 뒤 이 엔드포인트를 불러야 접수 파드들이 다시 요청을 받는다.
 *
 * <p>플래그를 푸는 것과 재고를 채우는 것을 한 동작으로 묶지 않았다. 재고 없이 플래그만
 * 풀리면 접수가 열리고 전부 탈락하므로, 순서를 사람이 정하게 남겨둔다.
 */
@RestController
@RequestMapping("/api/products/flash-sale")
@RequiredArgsConstructor
public class FlashSaleAdminController {

    private final SoldOutRegistry soldOutRegistry;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/{variantId}/reopen")
    public ApiResponse<Void> reopen(@PathVariable Long variantId) {
        soldOutRegistry.clear(variantId);
        kafkaTemplate.send(KafkaTopics.FLASH_SALE_SOLD_OUT, String.valueOf(variantId),
                Map.of("variantId", variantId, "soldOut", false));
        return ApiResponse.ok(null);
    }
}
