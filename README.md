# E-Commerce Microservices Platform

[English](README.md) | [한국어](README-ko.md) | [中文](README-zh.md)

A Spring Boot-based microservices platform for an e-commerce domain (Product, Order, Payment, Customer). The system is built around Domain-Driven Design, event-driven communication via Kafka, and synchronous inter-service calls via RestClient, and is packaged for both local Docker Compose and Kubernetes deployment.

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Kafka](https://img.shields.io/badge/Kafka_KRaft-231F20?style=flat-square&logo=apache-kafka&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-326CE5?style=flat-square&logo=kubernetes&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white)

## Overview

This project implements an e-commerce backend as a set of cooperating microservices. Each service owns its own database and communicates with peers through a mix of synchronous REST calls (for read-side lookups and stock reservations) and asynchronous Kafka events (for order/payment orchestration). The codebase is deliberately kept free of over-engineered abstractions so the boundaries between services, the event flow, and the deployment topology remain easy to reason about.

## System Architecture

![System Architecture](docs/domain/diagrams/system-architecture.png)

## Core Architecture

The system employs several architectural patterns:

- **Microservices Architecture**: Independent services per bounded context, each with its own database
- **Domain-Driven Design**: Rich domain models, aggregates, value objects, and domain events per bounded context
- **Event-Driven Architecture**: Asynchronous communication via Kafka for cross-service orchestration
- **Synchronous Inter-Service Calls**: Spring `RestClient` with configurable timeouts and error handling for read-side lookups and stock operations
- **Database-per-Service**: Each service owns a dedicated MySQL schema; no shared tables
- **Layered Architecture per Service**: `api` → `application` → `domain` → `infra` package separation
- **Shared Kernel (`common` module)**: `BaseEntity`, `ApiResponse`, `PageResponse`, `GlobalExceptionHandler`, `BusinessException`, `DomainEvent`, `KafkaTopics`, and cross-cutting Spring configs

## Technology Stack

**Languages & Frameworks**

- Java 21 (LTS, virtual threads support)
- Spring Boot 3.x
- Spring Data JPA / Hibernate
- QueryDSL 5.1 (type-safe queries)
- Spring Kafka
- jBCrypt (password hashing)

**Data & Messaging**

- MySQL 8.0 (transactional data, database-per-service)
- Apache Kafka in KRaft mode (event streaming, no ZooKeeper)

**API & Documentation**

- Spring Web (REST controllers)
- springdoc-openapi (Swagger UI per service)
- Jakarta Bean Validation (request validation)

**Build & Deployment**

- Gradle multi-module
- Docker & Docker Compose (local development)
- Kubernetes (namespace, ConfigMap, Secrets, StatefulSet, Deployment, Ingress)
- GitHub Actions (CI)

**Testing**

- JUnit 5
- Spring Boot Test slices
- Testcontainers (MySQL, Kafka)

## Microservices

| Service | Port | Responsibility | Key Aggregates |
|---|---|---|---|
| **service-product** | 8081 | Product catalog, brand, stock | `Product`, `ProductVariant`, `ProductImage`, `Brand` |
| **service-order** | 8082 | Order creation, lifecycle, cancellation | `Order`, `OrderItem`, `ShippingAddress` |
| **service-payment** | 8083 | Payment processing and refunds | `Payment` |
| **service-customer** | 8084 | Customer profile and addresses | `Customer`, `CustomerAddress` |

Each service exposes its API under `/api/{resource}` and its OpenAPI spec at `/swagger-ui.html`.

## Domain Documentation

Domain-driven design artifacts live in [`docs/domain/`](docs/domain/):

- [Ubiquitous Language](docs/domain/01-ubiquitous-language.md)
- [Bounded Context Map](docs/domain/02-bounded-context-map.md)
- [Use Cases](docs/domain/03-use-cases.md)
- [Aggregate Design](docs/domain/04-aggregate-design.md)

## Kafka Events

Services communicate asynchronously through domain events defined in `common/KafkaTopics.java`:

| Topic | Producer | Consumer(s) | Purpose |
|---|---|---|---|
| `order.created` | Order | Payment | Trigger payment processing |
| `order.cancelled` | Order | Payment | Trigger refund if already paid |
| `payment.completed` | Payment | Order | Mark order as paid |
| `payment.failed` | Payment | Order | Cancel order |
| `product.stock-reserved` | Product | (audit / future) | Stock reservation audit trail |
| `product.stock-released` | Product | (audit / future) | Stock release audit trail |
| `customer.registered` | Customer | (notification / future) | New customer signup |

## Project Structure

```
ecommerce-microservices/
├── backend-v2/              # Gradle multi-module backend
│   ├── common/              # Shared kernel (BaseEntity, ApiResponse, configs, KafkaTopics)
│   ├── service-product/     # Product service (8081)
│   ├── service-order/       # Order service (8082)
│   ├── service-payment/     # Payment service (8083)
│   ├── service-customer/    # Customer service (8084)
│   ├── build.gradle         # Root build with dependency management
│   └── settings.gradle
├── infra/                   # Docker Compose files and infra config
├── k8s/                     # Kubernetes manifests
│   ├── namespace.yml
│   ├── base/                # MySQL StatefulSet, Kafka deployment, ConfigMap, Secrets
│   ├── services/            # Per-service Deployment + Service manifests
│   └── ingress/             # nginx IngressRoute
├── scripts/                 # Helper scripts (k8s-deploy.sh, k8s-teardown.sh, etc.)
└── frontend/                # Next.js 16 storefront (separate track)
```

## Setup & Configuration

### Prerequisites

- Java 21+
- Docker & Docker Compose
- Gradle 8.x (wrapper included)
- (Optional) `kubectl` + a local Kubernetes cluster (k3s, minikube, kind, or Docker Desktop)

### Quick Start (Docker Compose)

```bash
# 1. Clone the repository
git clone https://github.com/lsh1215/ecommerce-microservices.git
cd ecommerce-microservices

# 2. Start MySQL + Kafka locally
docker compose -f infra/docker-compose.yml up -d

# 3. Build the backend
cd backend-v2
./gradlew build -x test

# 4. Run each service (separate terminals, or use your IDE)
./gradlew :service-product:bootRun
./gradlew :service-order:bootRun
./gradlew :service-payment:bootRun
./gradlew :service-customer:bootRun
```

Each service activates the `local` profile by default, connecting to `localhost:3306` (MySQL) and `localhost:9092` (Kafka).

### Verify

```bash
# Health checks
curl http://localhost:8081/actuator/health   # product
curl http://localhost:8082/actuator/health   # order
curl http://localhost:8083/actuator/health   # payment
curl http://localhost:8084/actuator/health   # customer

# Swagger UI
open http://localhost:8081/swagger-ui.html
```

### Kubernetes Deployment

```bash
# Apply namespace, base infra, services, and ingress
./scripts/k8s-deploy.sh

# Tear down
./scripts/k8s-teardown.sh
```

Manifests use the `k8s` profile, which resolves MySQL/Kafka through in-cluster service DNS and tightens CORS and `ddl-auto`.

## Contributing

Contributions, issues, and feature requests are welcome. Please open an issue to discuss significant changes before submitting a pull request.

## License

This project is released under the [MIT License](LICENSE).
