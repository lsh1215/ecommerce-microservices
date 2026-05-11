# Ubiquitous Language — 4 Bounded Contexts

## 개요

이 문서는 일반 이커머스 플랫폼의 4개 Bounded Context에서 사용하는 핵심 도메인 용어를 정의한다.
모든 팀원(개발자, 기획자, 디자이너)은 이 용어를 동일한 의미로 사용해야 한다.

> 본 프로젝트는 가상계좌(무통장입금) 결제 시나리오를 모델링한다. PG/은행 webhook 연동은 범위 밖이며 Payment 서비스 내부 로직으로 시뮬레이션한다.

---

## 1. Product Context

| Term | 정의 |
|------|------|
| **Product** | 판매 가능한 상품. 이름, 설명, 기본 가격, 카테고리, 상태(ACTIVE/INACTIVE)를 가진다. |
| **ProductVariant** | Product의 구체적인 판매 단위. 사이즈, 색상, SKU, 재고 수량, 선택적 가격 오버라이드를 가진다. |
| **ProductImage** | Product에 연결된 이미지. 정렬 순서와 대표 이미지 여부(isPrimary)를 가진다. |
| **Brand** | 상품을 제조하거나 유통하는 브랜드. 이름, 설명, 로고 URL, 원산지 국가를 가진다. |
| **Category** | 상품 분류 체계. 문자열 기반으로 상품을 그룹화한다. |
| **Stock** | ProductVariant가 보유한 재고 수량. 예약(reserve)과 해제(release) 연산을 지원한다. |
| **SKU** | Stock Keeping Unit. ProductVariant를 고유하게 식별하는 코드. |

---

## 2. Order Context

| Term | 정의 |
|------|------|
| **Order** | 고객이 생성한 주문. 주문번호, 상태, 총 금액, 배송지, 메모를 포함한다. |
| **OrderItem** | Order에 포함된 개별 상품 항목. 주문 시점의 상품 정보(이름, 가격, 변형 정보)를 스냅샷한다. |
| **OrderNumber** | ULID 기반으로 생성되는 주문 고유 식별자. |
| **OrderStatus** | 주문의 생명주기 상태. PENDING → CONFIRMED → PAID → SHIPPING → DELIVERED 또는 CANCELLED. |
| **ShippingAddress** | 배송 수령지 정보. 수령인, 전화번호, 우편번호, 주소로 구성된 Value Object. |
| **Stock Reservation** | 주문 생성 시 Product Context에 재고 확보를 요청하는 행위. |
| **ExpiresAt** | 가상계좌 입금기한. Order 생성 시점에 `now + 7일` 로 세팅된다. |

---

## 3. Payment Context

| Term | 정의 |
|------|------|
| **Payment** | 특정 Order에 대한 결제 건. 금액, 상태, 거래 ID를 가진다. |
| **PaymentStatus** | 결제의 생명주기 상태. PENDING → COMPLETED 또는 FAILED. COMPLETED에서 REFUNDED로 전이 가능. |
| **VirtualAccountInstruction** | Order 생성 시 결정적으로 발급되는 입금 안내(은행, 가상계좌번호, 예금주, 금액, 입금기한). Order Aggregate 의 Embedded Value Object. |
| **TransactionId** | 결제 처리 시 부여되는 고유 거래 식별자. (Payment 서비스 내부에서 시뮬레이션 발급) |
| **Refund** | 완료된 결제를 취소하고 금액을 반환하는 행위. |

---

## 4. Customer Context

| Term | 정의 |
|------|------|
| **Customer** | 플랫폼에 가입한 사용자. 이메일(고유), 비밀번호(해시), 이름, 전화번호를 가진다. |
| **CustomerAddress** | Customer가 등록한 배송지 주소. 라벨(HOME/WORK/OTHER), 기본 배송지 여부를 가진다. |
| **Credentials** | Customer의 인증 정보. 이메일과 BCrypt로 해시된 비밀번호로 구성된다. |
| **DefaultAddress** | Customer의 주소 목록 중 기본으로 선택된 배송지. |

---

## Context 간 공유 용어

| Term | 사용 Context | 정의 |
|------|-------------|------|
| **CustomerId** | Order, Customer | Customer를 식별하는 Long 타입 ID. Order Context에서는 외래 참조로 사용. |
| **OrderId** | Order, Payment | Order를 식별하는 Long 타입 ID. Payment Context에서는 결제 대상을 가리킨다. |
| **ProductId / ProductVariantId** | Product, Order | Product를 식별하는 ID. Order Context에서는 주문 항목의 참조로 사용. |
