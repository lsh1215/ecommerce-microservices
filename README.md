# E-Commerce Order Platform

[English](README.md) | [한국어](README-ko.md) | [中文](README-zh.md)

A backend-focused e-commerce platform designed to experience the journey from **monolith to microservices**. Starting as a single Spring Boot application, this project will evolve through load testing, async messaging, service decomposition, and full observability — with measurable data at every step.

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![k6](https://img.shields.io/badge/k6-7D64FF?style=flat-square&logo=k6&logoColor=white)

## Table of Contents

- [Overview](#overview)
- [System Architecture](#system-architecture)
- [Tech Stack](#tech-stack)
- [Domain Model](#domain-model)
- [Key Design Decisions](#key-design-decisions)
- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [License](#license)

## Overview

The core idea: **don't introduce complexity until the data proves you need it.**

1. Build a monolith that handles Order, Payment, Product, and Search domains
2. Load test it until it breaks — capture p50/p95/p99 baselines
3. Introduce each technology (Kafka, MSA, Elasticsearch, Circuit Breaker) only to solve a specific, measured bottleneck

This is the **Phase 1 monolith** — a single Spring Boot application with all domains sharing one MySQL database and synchronous REST communication.

## System Architecture

> Single Spring Boot application serving all domains through a unified REST API.

<!-- Replace with actual architecture diagram -->
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
               │ MySQL  │
               └─────────────┘
```

### ERD

<!-- Replace with actual ERD screenshot -->
_To be added after domain implementation._

### API Endpoints

<!-- Replace with actual API documentation screenshot -->
_To be added after REST API implementation._

## Tech Stack

### Backend

| Category | Technology | Purpose |
|----------|-----------|---------|
| Language | Java 21 | LTS with virtual threads support |
| Framework | Spring Boot 3.x | Application framework |
| ORM | Spring Data JPA / Hibernate | Database access |
| Security | Spring Security + JWT | Authentication & authorization |
| Validation | Jakarta Bean Validation | Request validation |

### Data

| Category | Technology | Purpose |
|----------|-----------|---------|
| Database | MySQL | Primary relational data store |
| Search | MySQL LIKE / Full-text | Product search (baseline before ES) |

### Infrastructure & Tooling

| Category | Technology | Purpose |
|----------|-----------|---------|
| Container | Docker + Docker Compose | Local development environment |
| Build | Gradle | Build management |
| Load Test | k6 | Stress test, spike test for bottleneck identification |
| Testing | JUnit 5 + Testcontainers | Unit, slice, integration tests |

## Domain Model

### Domains

| Domain | Responsibility | Key Entities |
|--------|---------------|-------------|
| **Order** | Order creation, status tracking, history | `Order`, `OrderItem`, `OrderStatus` |
| **Payment** | Synchronous payment processing, refunds | `Payment`, `PaymentStatus` |
| **Product** | Catalog management, inventory | `Product`, `Category`, `Inventory` |
| **Search** | Product search by keyword, filtering | Delegates to Product domain (DB query) |

### Inter-Domain Communication (Monolith)

In the monolith phase, all communication is **synchronous method calls** within the same JVM:

```
OrderService.placeOrder()
  → ProductService.reserveInventory()
  → PaymentService.processPayment()
  → Order status updated
```

This synchronous coupling is intentional — it becomes the **measured bottleneck** that justifies Kafka introduction in Phase 3.

### Package Structure

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
    ├── config/            # Spring configuration
    ├── exception/         # Global exception handler
    └── dto/               # Shared DTOs (PageResponse, ErrorResponse)
```

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Architecture | Monolith-first | Establish baselines before introducing distributed complexity |
| Database | Single shared MySQL | Intentional coupling to measure lock contention under load |
| Search | MySQL LIKE query | Baseline before Elasticsearch; measures degradation at scale |
| Testing | TDD per layer | Unit (domain) → Slice (@WebMvcTest, @DataJpaTest) → Integration (Testcontainers) |

## Getting Started

### Prerequisites

- Java 21+
- Docker & Docker Compose
- Gradle 8.x (wrapper included)

### Setup

```bash
git clone https://github.com/<your-username>/ecommerce-microservices.git
cd ecommerce-microservices

# Start MySQL
docker compose up -d mysql

# Build & run
./gradlew bootRun
```

### Verify

```bash
# Health check
curl http://localhost:8080/actuator/health

# API example
curl http://localhost:8080/api/v1/products
```

## Project Structure

```
ecommerce-microservices/
├── backend/               # Spring Boot monolith
├── frontend/              # Frontend (TBD)
├── docs/
│   ├── adr/               # Architecture Decision Records
│   └── performance/       # Load test results & analysis
├── infra/                 # Docker Compose, infrastructure config
├── k6/                    # Load test scripts
└── scripts/               # Utility scripts
```

## License

This project is licensed under the [MIT License](LICENSE).
