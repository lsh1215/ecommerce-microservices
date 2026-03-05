# E-Commerce Order Platform

[English](README.md) | [한국어](README-ko.md) | [中文](README-zh.md)

**모놀리스에서 마이크로서비스로의 전환 과정**을 직접 경험하기 위한 백엔드 중심 이커머스 플랫폼입니다. 단일 Spring Boot 애플리케이션에서 시작하여, 부하 테스트 → 비동기 메시징 → 서비스 분리 → 관측성 확보까지, 매 단계마다 측정 가능한 데이터를 기반으로 발전시킵니다.

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![k6](https://img.shields.io/badge/k6-7D64FF?style=flat-square&logo=k6&logoColor=white)

## 목차

- [개요](#개요)
- [시스템 아키텍처](#시스템-아키텍처)
- [기술 스택](#기술-스택)
- [도메인 모델](#도메인-모델)
- [주요 설계 결정](#주요-설계-결정)
- [시작하기](#시작하기)
- [프로젝트 구조](#프로젝트-구조)
- [라이선스](#라이선스)

## 개요

핵심 원칙: **데이터가 필요성을 증명하기 전까지 복잡성을 도입하지 않는다.**

1. Order, Payment, Product, Search 도메인을 포함하는 모놀리스 구축
2. 부하 테스트로 한계점 확인 — p50/p95/p99 기준 지표 측정
3. 각 기술(Kafka, MSA, Elasticsearch, Circuit Breaker)은 측정된 병목을 해결하기 위해서만 도입

현재는 **Phase 1 모놀리스** — 모든 도메인이 하나의 MySQL 데이터베이스를 공유하는 단일 Spring Boot 애플리케이션입니다.

## 시스템 아키텍처

> 모든 도메인을 하나의 REST API로 서빙하는 단일 Spring Boot 애플리케이션.

<!-- 실제 아키텍처 다이어그램으로 교체 예정 -->
```
┌────────────────────────────────────────────────────┐
│                  Spring Boot (:8080)                │
├────────────────────────────────────────────────────┤
│                                                    │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐      │
│  │   Order   │  │  Payment  │  │  Product  │      │
│  │ Controller│  │ Controller│  │ Controller│      │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘      │
│        ▼              ▼              ▼             │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐      │
│  │   Order   │  │  Payment  │  │  Product  │      │
│  │  Service  │─→│  Service  │  │  Service  │      │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘      │
│        │              │              │             │
│        ▼              ▼              ▼             │
│  ┌─────────────────────────────────────────┐       │
│  │           Spring Data JPA               │       │
│  └──────────────────┬──────────────────────┘       │
│                     │                              │
└─────────────────────┼──────────────────────────────┘
                      ▼
               ┌─────────────┐
               │    MySQL    │
               └─────────────┘
```

### ERD

<!-- 실제 ERD 캡처로 교체 예정 -->
_도메인 구현 후 추가 예정._

### API 엔드포인트

<!-- 실제 API 문서 캡처로 교체 예정 -->
_REST API 구현 후 추가 예정._

## 기술 스택

### 백엔드

| 분류 | 기술 | 용도 |
|------|-----|------|
| 언어 | Java 21 | Virtual threads 지원 LTS |
| 프레임워크 | Spring Boot 3.x | 애플리케이션 프레임워크 |
| ORM | Spring Data JPA / Hibernate | 데이터베이스 접근 |
| 보안 | Spring Security + JWT | 인증 및 인가 |
| 검증 | Jakarta Bean Validation | 요청 데이터 검증 |

### 데이터

| 분류 | 기술 | 용도 |
|------|-----|------|
| 데이터베이스 | MySQL | 주 관계형 데이터 저장소 |
| 검색 | MySQL LIKE / Full-text | 상품 검색 (ES 도입 전 기준선) |

### 인프라 & 도구

| 분류 | 기술 | 용도 |
|------|-----|------|
| 컨테이너 | Docker + Docker Compose | 로컬 개발 환경 |
| 빌드 | Gradle | 빌드 관리 |
| 부하 테스트 | k6 | 스트레스/스파이크 테스트 |
| 테스트 | JUnit 5 + Testcontainers | 단위, 슬라이스, 통합 테스트 |

## 도메인 모델

### 도메인

| 도메인 | 책임 | 주요 엔티티 |
|--------|-----|------------|
| **Order** | 주문 생성, 상태 관리, 주문 이력 | `Order`, `OrderItem`, `OrderStatus` |
| **Payment** | 동기 결제 처리, 환불 | `Payment`, `PaymentStatus` |
| **Product** | 상품 카탈로그, 재고 관리 | `Product`, `Category`, `Inventory` |
| **Search** | 키워드 상품 검색, 필터링 | Product 도메인에 위임 (DB 쿼리) |

### 도메인 간 통신 (모놀리스)

모놀리스 단계에서는 모든 통신이 동일 JVM 내 **동기 메서드 호출**로 이루어집니다:

```
OrderService.placeOrder()
  → ProductService.reserveInventory()
  → PaymentService.processPayment()
  → Order 상태 업데이트
```

이 동기 결합은 의도적입니다 — Phase 3에서 Kafka 도입을 정당화하는 **측정된 병목 지점**이 됩니다.

### 패키지 구조

```
com.ecommerce/
├── order/
│   ├── controller/        # OrderController
│   ├── service/           # OrderService
│   ├── repository/        # OrderRepository
│   ├── entity/            # Order, OrderItem, OrderStatus
│   └── dto/               # OrderRequest, OrderResponse
├── payment/
│   ├── controller/        # PaymentController
│   ├── service/           # PaymentService
│   ├── repository/        # PaymentRepository
│   ├── entity/            # Payment, PaymentStatus
│   └── dto/               # PaymentRequest, PaymentResponse
├── product/
│   ├── controller/        # ProductController
│   ├── service/           # ProductService
│   ├── repository/        # ProductRepository
│   ├── entity/            # Product, Category
│   └── dto/               # ProductRequest, ProductResponse
└── common/
    ├── config/            # Spring 설정
    ├── exception/         # 글로벌 예외 처리
    └── dto/               # 공용 DTO (PageResponse, ErrorResponse)
```

## 주요 설계 결정

| 결정 사항 | 선택 | 근거 |
|----------|------|------|
| 아키텍처 | 모놀리스 우선 | 분산 복잡성 도입 전 기준선 확보 |
| 데이터베이스 | 단일 MySQL 공유 | 부하 시 락 경합을 측정하기 위한 의도적 결합 |
| 검색 | MySQL LIKE 쿼리 | Elasticsearch 도입 전 기준선; 규모 확대 시 성능 저하 측정 |
| 테스트 | 레이어별 TDD | Unit (도메인) → Slice (@WebMvcTest, @DataJpaTest) → Integration (Testcontainers) |

## 시작하기

### 사전 요구사항

- Java 21+
- Docker & Docker Compose
- Gradle 8.x (wrapper 포함)

### 설치 및 실행

```bash
git clone https://github.com/<your-username>/ecommerce-microservices.git
cd ecommerce-microservices

# MySQL 시작
docker compose up -d mysql

# 빌드 및 실행
./gradlew bootRun
```

### 동작 확인

```bash
# 헬스 체크
curl http://localhost:8080/actuator/health

# API 예시
curl http://localhost:8080/api/v1/products
```

## 프로젝트 구조

```
ecommerce-microservices/
├── backend/               # Spring Boot 모놀리스
├── frontend/              # 프론트엔드 (추후 결정)
├── docs/
│   ├── adr/               # Architecture Decision Records
│   └── performance/       # 부하 테스트 결과 및 분석
├── infra/                 # Docker Compose, 인프라 설정
├── k6/                    # 부하 테스트 스크립트
└── scripts/               # 유틸리티 스크립트
```

## 라이선스

이 프로젝트는 [MIT 라이선스](LICENSE)에 따라 배포됩니다.
