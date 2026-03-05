# E-Commerce Order Platform

[English](README.md) | [한국어](README-ko.md) | [中文](README-zh.md)

一个以后端为核心的电商平台，旨在体验从**单体架构到微服务架构**的完整演进过程。从单一 Spring Boot 应用起步，通过压力测试、异步消息、服务拆分和可观测性建设逐步演进——每一步都以可量化的数据为驱动。

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![k6](https://img.shields.io/badge/k6-7D64FF?style=flat-square&logo=k6&logoColor=white)

## 目录

- [概述](#概述)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [领域模型](#领域模型)
- [关键设计决策](#关键设计决策)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [许可证](#许可证)

## 概述

核心理念：**在数据证明其必要性之前，不引入复杂性。**

1. 构建包含 Order、Payment、Product、Search 领域的单体应用
2. 通过压力测试找到瓶颈——获取 p50/p95/p99 基准指标
3. 仅在解决已测量到的具体瓶颈时，才引入相应技术（Kafka、微服务、Elasticsearch、熔断器）

当前为 **Phase 1 单体架构**——所有领域共享同一个 MySQL 数据库的单一 Spring Boot 应用，采用同步 REST 通信。

## 系统架构

> 单一 Spring Boot 应用通过统一的 REST API 提供所有领域服务。

<!-- 后续替换为实际架构图 -->
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

<!-- 后续替换为实际 ERD 截图 -->
_领域实现后添加。_

### API 端点

<!-- 后续替换为实际 API 文档截图 -->
_REST API 实现后添加。_

## 技术栈

### 后端

| 分类 | 技术 | 用途 |
|------|-----|------|
| 语言 | Java 21 | 支持虚拟线程的 LTS 版本 |
| 框架 | Spring Boot 3.x | 应用框架 |
| ORM | Spring Data JPA / Hibernate | 数据库访问 |
| 安全 | Spring Security + JWT | 身份认证与授权 |
| 校验 | Jakarta Bean Validation | 请求数据校验 |

### 数据

| 分类 | 技术 | 用途 |
|------|-----|------|
| 数据库 | MySQL | 主关系型数据存储 |
| 搜索 | MySQL LIKE / Full-text | 商品搜索（ES 引入前的基准） |

### 基础设施与工具

| 分类 | 技术 | 用途 |
|------|-----|------|
| 容器 | Docker + Docker Compose | 本地开发环境 |
| 构建 | Gradle | 构建管理 |
| 压测 | k6 | 压力测试、峰值测试 |
| 测试 | JUnit 5 + Testcontainers | 单元、切片、集成测试 |

## 领域模型

### 领域

| 领域 | 职责 | 核心实体 |
|------|-----|---------|
| **Order** | 订单创建、状态跟踪、订单历史 | `Order`, `OrderItem`, `OrderStatus` |
| **Payment** | 同步支付处理、退款 | `Payment`, `PaymentStatus` |
| **Product** | 商品目录管理、库存管理 | `Product`, `Category`, `Inventory` |
| **Search** | 关键词商品搜索、过滤 | 委托给 Product 领域（数据库查询） |

### 领域间通信（单体）

在单体阶段，所有通信都是同一 JVM 内的**同步方法调用**：

```
OrderService.placeOrder()
  → ProductService.reserveInventory()
  → PaymentService.processPayment()
  → Order 状态更新
```

这种同步耦合是有意为之的——它将成为 Phase 3 引入 Kafka 的**可量化瓶颈依据**。

### 包结构

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
    ├── config/            # Spring 配置
    ├── exception/         # 全局异常处理
    └── dto/               # 公共 DTO（PageResponse, ErrorResponse）
```

## 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 架构 | 单体优先 | 在引入分布式复杂性之前建立基准线 |
| 数据库 | 单一共享 MySQL | 有意耦合，用于测量高负载下的锁竞争 |
| 搜索 | MySQL LIKE 查询 | Elasticsearch 引入前的基准线；测量规模增长时的性能退化 |
| 测试 | 分层 TDD | Unit（领域）→ Slice（@WebMvcTest, @DataJpaTest）→ Integration（Testcontainers） |

## 快速开始

### 前置要求

- Java 21+
- Docker & Docker Compose
- Gradle 8.x（已包含 wrapper）

### 安装与运行

```bash
git clone https://github.com/<your-username>/ecommerce-microservices.git
cd ecommerce-microservices

# 启动 MySQL
docker compose up -d mysql

# 构建并运行
./gradlew bootRun
```

### 验证

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# API 示例
curl http://localhost:8080/api/v1/products
```

## 项目结构

```
ecommerce-microservices/
├── backend/               # Spring Boot 单体应用
├── frontend/              # 前端（待定）
├── docs/
│   ├── adr/               # 架构决策记录
│   └── performance/       # 压测结果与分析
├── infra/                 # Docker Compose、基础设施配置
├── k6/                    # 压测脚本
└── scripts/               # 工具脚本
```

## 许可证

本项目基于 [MIT 许可证](LICENSE) 发布。
