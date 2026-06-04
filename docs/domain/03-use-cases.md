# Use Cases

## 개요

4개 Bounded Context의 핵심 유스케이스를 정의한다.

---

## 1. Product Context

### UC-P1: 상품 목록 조회

- **Actor**: 고객, 관리자
- **선행 조건**: 없음
- **주요 흐름**:
  1. Actor가 상품 목록을 요청한다 (페이지, 정렬, 필터 조건 포함).
  2. 시스템이 조건에 맞는 상품을 페이징하여 반환한다.
- **필터 조건**: 브랜드, 카테고리, 가격 범위, 키워드 검색
- **결과**: 페이징된 상품 목록 (이름, 가격, 대표 이미지, 브랜드)

### UC-P2: 상품 상세 조회

- **Actor**: 고객, 관리자
- **선행 조건**: 해당 Product가 존재
- **주요 흐름**:
  1. Actor가 상품 ID로 상세 정보를 요청한다.
  2. 시스템이 상품 정보, Variant 목록(재고 포함), 이미지 목록을 반환한다.
- **결과**: 상품 상세 (Variant별 재고, 이미지 포함)

### UC-P3: 상품 등록 (Admin)

- **Actor**: 관리자
- **선행 조건**: Brand가 존재
- **주요 흐름**:
  1. 관리자가 상품 정보(이름, 설명, 가격, 카테고리, Brand ID)를 입력한다.
  2. 시스템이 Product를 생성하고 ID를 반환한다.
- **대안 흐름**:
  - 필수 필드 누락 시 검증 오류를 반환한다.
  - 존재하지 않는 Brand ID 시 NOT_FOUND 오류를 반환한다.

### UC-P4: 상품 수정 (Admin)

- **Actor**: 관리자
- **선행 조건**: 해당 Product가 존재
- **주요 흐름**:
  1. 관리자가 변경할 필드를 입력한다.
  2. 시스템이 Product를 업데이트한다.

### UC-P5: 재고 예약

- **Actor**: Order Context (시스템 간 호출)
- **선행 조건**: ProductVariant가 존재하고 충분한 재고가 있음
- **주요 흐름**:
  1. Order Context가 variantId와 수량으로 재고 예약을 요청한다.
  2. 시스템이 재고를 차감하고 성공을 반환한다.
  3. `product.stock-reserved` 이벤트를 발행한다.
- **대안 흐름**:
  - 재고 부족 시 INSUFFICIENT_STOCK 오류를 반환한다.

### UC-P6: 재고 해제

- **Actor**: Order Context (시스템 간 호출)
- **선행 조건**: 이전에 예약된 재고가 존재
- **주요 흐름**:
  1. Order Context가 variantId와 수량으로 재고 해제를 요청한다.
  2. 시스템이 재고를 복구하고 성공을 반환한다.
  3. `product.stock-released` 이벤트를 발행한다.

### UC-P7: 브랜드 관리

- **Actor**: 관리자
- **선행 조건**: 없음
- **주요 흐름**:
  1. 브랜드 목록 조회, 상세 조회, 등록, 수정을 수행한다.
- **대안 흐름**:
  - 동일 이름의 브랜드가 존재할 경우 DUPLICATE_BRAND 오류를 반환한다.

---

## 2. Order Context

### UC-O1: 주문 생성

- **Actor**: 고객
- **선행 조건**: API Gateway에서 식별된 고객 ID가 있고, 주문 항목의 ProductVariant에 충분한 재고가 있음
- **주요 흐름**:
  1. 고객이 주문 항목(variantId, 수량), 배송지, 메모를 입력한다.
  2. 시스템이 Product Context에 각 항목의 재고를 예약한다.
  3. 시스템이 Order를 생성한다 (상태: PENDING, expiresAt = now + 15분).
  4. `order.created` 이벤트를 Kafka로 발행한다.
  5. Payment Context가 이벤트를 수신해 PG 승인 요청을 시작한다.
- **대안 흐름**:
  - 재고 예약 실패 시 이미 예약된 재고를 해제하고 INSUFFICIENT_STOCK 오류를 반환한다.
- **결과**: 생성된 Order (상태 PENDING). 결제 완료 여부는 `payment.completed` 또는 `payment.failed` 이벤트로 후속 반영된다.

### UC-O2: 주문 상세 조회

- **Actor**: 고객
- **선행 조건**: 해당 Order가 존재
- **주요 흐름**:
  1. 고객이 주문 ID로 상세 정보를 요청한다.
  2. 시스템이 주문 정보, 주문 항목, 배송지를 반환한다.

### UC-O3: 내 주문 목록 조회

- **Actor**: 고객
- **선행 조건**: 없음
- **주요 흐름**:
  1. 고객이 자신의 주문 목록을 요청한다 (페이징).
  2. 시스템이 customerId로 주문 목록을 반환한다.

### UC-O4: 주문 취소

- **Actor**: 고객
- **선행 조건**: Order가 존재하고 취소 가능한 상태 (PENDING 또는 CONFIRMED)
- **주요 흐름**:
  1. 고객이 주문 취소를 요청한다.
  2. 시스템이 주문 상태를 CANCELLED로 변경한다.
  3. Product Context에 재고 해제를 요청한다.
  4. `order.cancelled` 이벤트를 Kafka로 발행한다.
- **대안 흐름**:
  - 이미 PAID 이상 상태이면 취소 불가 오류를 반환한다.

---

## 3. Payment Context

### UC-PM1: 결제 처리

- **Actor**: 시스템 (Kafka consumer)
- **선행 조건**: 해당 Order가 존재
- **주요 흐름**:
  1. `order.created` 이벤트를 수신한다.
  2. Payment를 생성한다 (상태: PENDING).
  3. PaymentAttempt를 생성하고 REQUESTED 이력을 저장한다.
  4. PaymentAttemptProcessor가 처리 대상 attempt를 claim한다.
  5. 트랜잭션 밖에서 PG 승인 adapter를 호출한다.
  6. 성공 시 PaymentAttempt와 Payment를 COMPLETED로 변경하고 `payment.completed` 이벤트를 발행한다.
- **대안 흐름**:
  - 결제 실패 시 상태를 FAILED로 변경하고 `payment.failed` 이벤트를 발행한다.
  - 재시도 가능한 PG 오류는 PaymentAttempt를 RETRYABLE_FAILED로 기록하고 다음 processor 주기에 재시도한다.

### UC-PM2: 환불 처리

- **Actor**: 시스템 또는 관리자
- **선행 조건**: Payment가 COMPLETED 상태
- **주요 흐름**:
  1. 환불 요청을 받는다.
  2. 결제 상태를 REFUNDED로 변경한다.
  3. 환불 시각을 기록한다.

### UC-PM3: 결제 상태 조회

- **Actor**: 고객, Order Context
- **선행 조건**: 해당 Order에 대한 Payment가 존재
- **주요 흐름**:
  1. orderId로 결제 정보를 조회한다.
  2. 결제 상태, 금액, 거래 ID를 반환한다.

---

## 4. Customer Context

### UC-C1: 회원 가입

- **Actor**: 방문자
- **선행 조건**: 없음
- **주요 흐름**:
  1. 방문자가 이메일, 비밀번호, 이름, 전화번호를 입력한다.
  2. 시스템이 이메일 중복을 검사한다.
  3. 비밀번호를 BCrypt로 해시하여 Customer를 생성한다.
  4. `customer.registered` 이벤트를 발행한다.
- **대안 흐름**:
  - 이메일이 이미 존재하면 DUPLICATE_EMAIL 오류를 반환한다.

### UC-C2: 로그인

- **Actor**: 고객
- **선행 조건**: 회원 가입 완료
- **주요 흐름**:
  1. 고객이 이메일, 비밀번호를 입력한다.
  2. 시스템이 이메일로 Customer를 조회하고 비밀번호를 검증한다.
  3. 고객 정보(ID, 이메일, 이름)를 반환한다.
- **대안 흐름**:
  - 이메일이 존재하지 않거나 비밀번호가 틀리면 INVALID_CREDENTIALS 오류를 반환한다.

### UC-C3: 프로필 수정

- **Actor**: 고객
- **선행 조건**: 로그인 상태
- **주요 흐름**:
  1. 고객이 이름, 전화번호 등을 변경한다.
  2. 시스템이 Customer 정보를 업데이트한다.

### UC-C4: 배송지 관리

- **Actor**: 고객
- **선행 조건**: 로그인 상태
- **주요 흐름**:
  1. 배송지 목록 조회, 등록, 수정, 삭제를 수행한다.
  2. 기본 배송지 설정 시 기존 기본 배송지의 isDefault를 false로 변경한다.
- **대안 흐름**:
  - 다른 고객의 주소를 수정/삭제 시도하면 FORBIDDEN 오류를 반환한다.
