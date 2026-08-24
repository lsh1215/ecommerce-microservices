package com.ecommerce.order.application.dto;

/**
 * 접수 결과. 접수 시점에는 DB 에 아무것도 쓰지 않으므로 돌려줄 것은 이 두 값뿐이다.
 *
 * <p>{@code (partition, offset)} 이 <b>공정 순번이자 식별자</b>다. 별도로 id 를 만들지 않는
 * 이유가 여기 있다.
 *
 * <ul>
 *   <li>순번이다. 같은 상품은 같은 파티션에 도착 순서로 실리므로 offset 이 그대로 순번이다.</li>
 *   <li>유일하다. 파티션 안에서 offset 은 겹치지 않는다.</li>
 *   <li>멱등하다. 메시지가 재전송돼도 offset 이 같으므로 두 번 처리되지 않는다.</li>
 *   <li>증명된다. 순서 분쟁이 생기면 그 offset 을 재생해 보이면 된다.</li>
 * </ul>
 */
public record FlashSubmitResult(int partition, long offset) {

    /** 사용자에게 돌려줄 티켓 문자열. */
    public String ticket() {
        return partition + "-" + offset;
    }
}
