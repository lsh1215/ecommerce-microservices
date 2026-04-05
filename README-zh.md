# E-Commerce Microservices Platform

[English](README.md) | [한국어](README-ko.md) | [中文](README-zh.md)

基于 Spring Boot 的电商微服务平台，覆盖 Product、Order、Payment、Customer 四个领域。系统以领域驱动设计（DDD）为核心，通过 Kafka 事件驱动通信与 RestClient 同步调用相结合进行服务间交互，支持本地 Docker Compose 与生产 Kubernetes 部署。

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Kafka](https://img.shields.io/badge/Kafka_KRaft-231F20?style=flat-square&logo=apache-kafka&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-326CE5?style=flat-square&logo=kubernetes&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white)

## 概述

本项目将电商后端实现为一组相互协作的微服务。每个服务拥有独立的数据库，服务间通过同步 REST 调用（用于查询与库存预留）和 Kafka 异步事件（用于订单/支付编排）进行通信。代码刻意避免过度抽象，让服务边界、事件流与部署拓扑保持清晰易懂。

## 核心架构

系统采用以下架构模式：

- **微服务架构**：按限界上下文划分独立服务，每个服务拥有自己的数据库
- **领域驱动设计**：以限界上下文为单位构建聚合、值对象与领域事件
- **事件驱动架构**：通过 Kafka 实现服务间异步编排
- **同步服务调用**：使用 Spring `RestClient`，配置超时与错误处理，用于查询与库存预留
- **Database-per-Service**：各服务拥有独立的 MySQL Schema，无共享表
- **服务分层架构**：`api` → `application` → `domain` → `infra` 包结构
- **共享内核（`common` 模块）**：`BaseEntity`、`ApiResponse`、`PageResponse`、`GlobalExceptionHandler`、`BusinessException`、`DomainEvent`、`KafkaTopics` 以及通用 Spring 配置

## 技术栈

**语言与框架**

- Java 21（LTS，支持虚拟线程）
- Spring Boot 3.x
- Spring Data JPA / Hibernate
- QueryDSL 5.1（类型安全查询）
- Spring Kafka
- jBCrypt（密码哈希）

**数据与消息**

- MySQL 8.0（事务数据，数据库分离）
- Apache Kafka KRaft 模式（事件流，无 ZooKeeper）

**API 与文档**

- Spring Web（REST 控制器）
- springdoc-openapi（每个服务的 Swagger UI）
- Jakarta Bean Validation（请求校验）

**构建与部署**

- Gradle 多模块
- Docker & Docker Compose（本地开发）
- Kubernetes（namespace、ConfigMap、Secrets、StatefulSet、Deployment、Ingress）
- GitHub Actions（CI）

**测试**

- JUnit 5
- Spring Boot Test slice
- Testcontainers（MySQL、Kafka）

## 微服务

| 服务 | 端口 | 职责 | 核心聚合 |
|---|---|---|---|
| **service-product** | 8081 | 商品目录、品牌、库存 | `Product`、`ProductVariant`、`ProductImage`、`Brand` |
| **service-order** | 8082 | 订单创建、状态管理、取消 | `Order`、`OrderItem`、`ShippingAddress` |
| **service-payment** | 8083 | 支付处理、退款 | `Payment` |
| **service-customer** | 8084 | 客户资料、收货地址 | `Customer`、`CustomerAddress` |

每个服务在 `/api/{resource}` 暴露 REST 接口，并在 `/swagger-ui.html` 提供 OpenAPI 文档。

## 领域文档

DDD 设计文档位于 [`docs/domain/`](docs/domain/)：

- [Ubiquitous Language](docs/domain/01-ubiquitous-language.md)
- [Bounded Context Map](docs/domain/02-bounded-context-map.md)
- [Use Cases](docs/domain/03-use-cases.md)
- [Aggregate Design](docs/domain/04-aggregate-design.md)

## Kafka 事件

服务通过 `common/KafkaTopics.java` 中定义的领域事件进行异步通信：

| 主题 | 生产者 | 消费者 | 用途 |
|---|---|---|---|
| `order.created` | Order | Payment | 触发支付处理 |
| `order.cancelled` | Order | Payment | 已支付订单触发退款 |
| `payment.completed` | Payment | Order | 将订单状态更新为 PAID |
| `payment.failed` | Payment | Order | 取消订单 |
| `product.stock-reserved` | Product | (audit / 待定) | 库存预留审计日志 |
| `product.stock-released` | Product | (audit / 待定) | 库存释放审计日志 |
| `customer.registered` | Customer | (notification / 待定) | 新客户注册通知 |

## 项目结构

```
ecommerce-microservices/
├── backend-v2/              # Gradle 多模块后端
│   ├── common/              # 共享内核（BaseEntity、ApiResponse、通用配置、KafkaTopics）
│   ├── service-product/     # Product 服务 (8081)
│   ├── service-order/       # Order 服务 (8082)
│   ├── service-payment/     # Payment 服务 (8083)
│   ├── service-customer/    # Customer 服务 (8084)
│   ├── build.gradle         # 根构建 + 依赖管理
│   └── settings.gradle
├── infra/                   # Docker Compose 文件与基础设施配置
├── k8s/                     # Kubernetes 清单
│   ├── namespace.yml
│   ├── base/                # MySQL StatefulSet、Kafka Deployment、ConfigMap、Secrets
│   ├── services/            # 各服务 Deployment + Service 清单
│   └── ingress/             # nginx IngressRoute
├── scripts/                 # 辅助脚本 (k8s-deploy.sh、k8s-teardown.sh 等)
└── frontend/                # Next.js 16 店面（独立线）
```

## 安装与配置

### 前置要求

- Java 21+
- Docker & Docker Compose
- Gradle 8.x（已包含 wrapper）
- （可选）`kubectl` + 本地 Kubernetes 集群（k3s、minikube、kind 或 Docker Desktop）

### 快速开始（Docker Compose）

```bash
# 1. 克隆仓库
git clone https://github.com/lsh1215/ecommerce-microservices.git
cd ecommerce-microservices

# 2. 启动本地 MySQL + Kafka
docker compose -f infra/docker-compose.yml up -d

# 3. 构建后端
cd backend-v2
./gradlew build -x test

# 4. 运行每个服务（分别打开终端或使用 IDE）
./gradlew :service-product:bootRun
./gradlew :service-order:bootRun
./gradlew :service-payment:bootRun
./gradlew :service-customer:bootRun
```

各服务默认启用 `local` profile，连接 `localhost:3306`（MySQL）与 `localhost:9092`（Kafka）。

### 验证

```bash
# 健康检查
curl http://localhost:8081/actuator/health   # product
curl http://localhost:8082/actuator/health   # order
curl http://localhost:8083/actuator/health   # payment
curl http://localhost:8084/actuator/health   # customer

# Swagger UI
open http://localhost:8081/swagger-ui.html
```

### Kubernetes 部署

```bash
# 应用 namespace、基础设施、服务与 ingress
./scripts/k8s-deploy.sh

# 卸载
./scripts/k8s-teardown.sh
```

清单使用 `k8s` profile，通过集群内部 DNS 解析 MySQL/Kafka，并收紧 CORS 与 `ddl-auto` 配置。

## 贡献

欢迎贡献、Issue 与 Feature Request。对于较大的改动，建议先提 Issue 讨论后再提交 Pull Request。

## 许可证

本项目基于 [MIT License](LICENSE) 发布。
