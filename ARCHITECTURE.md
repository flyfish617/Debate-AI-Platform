# DebateAI - 架构设计文档

> 版本: v1.3 | 日期: 2026-06-14 | 状态: 对齐当前后端与前端实现

---

## 一、项目定位

**DebateAI** 是一个与 AI 进行有限轮次辩论对抗，并支持社区围观、投票、评论、通知和排行榜的在线思维训练平台。

### 核心决策

| 决策 | 选择 | 说明 |
|------|------|------|
| 辩论模式 | 纯 AI 对手 | 暂不做真人匹配，降低实时协作复杂度 |
| 回合设计 | 5-15 轮 | 创建时可设置最大轮次，默认/下限 5，上限 15；达到上限自动结辩，用户也可提前手动结束 |
| 社交深度 | 围观 + 投票 + 评论 | 形成公开讨论闭环 |
| AI 模型 | 单模型配置 | 基于 Spring AI 接入 OpenAI 兼容模型，当前阶段不做模型管理后台；需要更换模型时由开发者修改配置文件或环境变量 |
| 架构模式 | 前后端分离 | 前端独立部署，后端提供 Java REST API 与流式接口 |
| 后端语言 | Java | 采用 Spring Boot 3.x 构建业务 API、AI 编排和异步消费者 |
| 辩论风格 | 温和 / 激烈 | 创建辩论时选择，运行中暂不切换 |
| 数据库 | MySQL 8.0 | 关系型主存储 |
| 缓存 | Redis 7.x | JWT 黑名单、排行榜快照 |
| 消息队列 | RabbitMQ 3.x | 异步处理 AI 结辩生成和排行榜快照刷新 |
| 邮箱验证 | 暂不需要 | 后续可作为反滥用能力补充 |
| 话题审核 | MVP 暂不需要 | 需要保留举报、隐藏、封禁接口扩展位 |
| 软删除 | 核心内容建议需要 | 用户、话题、辩论、评论建议保留 `deleted_at` |

### MVP 边界

- 支持注册、登录、创建话题、创建辩论、AI 流式回复、手动结束辩论和轮次上限自动结束。
- 支持公开辩论围观、辩论结束后投票、评论、用户主页和排行榜。
- 不做真人实时对战、不做私信。
- MVP 提供简单管理员页面，用于举报处理、内容隐藏和用户禁用。
- 必须具备基础内容治理能力：举报、隐藏评论或话题、用户禁用状态。

---

## 二、系统架构

### 2.1 全景图

```text
                     ┌─── 浏览器 (Nuxt/Vue SPA) ───┐
                     │  Pinia / composables         │
                     │  Fetch Stream / HTTP         │
                     └─────────────┬────────────────┘
                                   │ HTTPS / JSON / Stream
                     ┌─────────────▼────────────────┐
                     │  Java Backend API             │
                     │  Spring Boot 3 / Spring MVC   │
                     │  Spring Security / JWT        │
                     │  Spring AI / AI Provider      │
                     │  MyBatis-Plus / SQL Migration │
                     └─────────────┬────────────────┘
                                   │
          ┌────────────────────────┼────────────────────────┐
          ▼                        ▼                        ▼
  ┌──────────────┐        ┌──────────────┐        ┌──────────────┐
  │  MySQL 8.0   │        │  Redis 7.x   │        │ RabbitMQ 3.x │
  │  持久化       │        │  缓存/限流    │        │  异步任务     │
  └──────────────┘        └──────────────┘        └──────┬───────┘
                                                          │
                                                   ┌──────▼───────┐
                                                   │ Spring 消费者   │
                                                   │ 结辩/排行榜     │
                                                   └──────────────┘
```

前端与后端完全分离：

- 前端只负责页面、状态管理、表单校验、流式读取和用户交互，不承载业务 API。
- 后端统一暴露 `/api/**` 接口，负责认证、业务规则、AI 调用、数据库事务、缓存、定时任务和 MQ 消费。
- 生产环境建议通过 Nginx 或 API Gateway 将前端静态站点与后端 API 统一到同一域名下，减少 CORS 和 Cookie/SameSite 配置复杂度。

### 2.2 通信方式分工

| 通道 | 用途 | 实现方式 | 说明 |
|------|------|----------|------|
| HTTP JSON | CRUD、认证、投票、评论 | 前端 `fetch` -> Spring MVC Controller | 标准请求响应 |
| 流式 AI 回复 | AI 辩论回复 | `POST + fetch ReadableStream` -> Java `StreamingResponseBody` 或 WebFlux `Flux` | 需要请求体，避免原生 EventSource 的 GET 限制 |
| RabbitMQ | AI 结辩生成、排行榜刷新 | Spring AMQP | 当前由同一个 Spring Boot 后端内的消费者处理，可按部署需要拆成独立 worker |

> 本项目不使用浏览器原生 `EventSource` 作为主要方案。`POST /api/debate/{id}/message` 返回 `text/event-stream` 风格的数据帧，但前端通过 `fetch` 读取 `ReadableStream`。

### 2.3 流式响应协议

**POST /api/debate/{id}/message**

```http
Content-Type: text/event-stream; charset=utf-8
Cache-Control: no-cache
Connection: keep-alive
```

```text
event: token
data: {"text":"这是"}

event: token
data: {"text":"回复"}

event: done
data: {"message_id":123,"round":4}

event: error
data: {"code":"AI_ERROR","message":"AI 服务暂时不可用"}
```

前端需要支持：

- 逐段追加 token。
- `done` 后关闭 reader。
- `error` 后展示重试入口。
- 页面离开或用户取消时调用 `AbortController.abort()`。

### 2.4 Redis 缓存策略

| 场景 | Key | 过期 | 写入策略 |
|------|-----|------|----------|
| 热门话题 | `topics:hot` | 5 min | 列表页读取，话题/辩论变更后删除 |
| 围观数 | `debate:{id}:viewers` | 辩论结束删除 | 进入页面增加，心跳续期 |
| JWT 黑名单 | `jwt:blacklist:{jti}` | = JWT 剩余有效期 | 登出或封禁时写入 |
| JWT 黑名单 | `jwt:blacklist:{jti}` | = JWT 剩余有效期 | 登出时写入 |
| 排行榜积分榜 | `debateai:leaderboard:points` | 10 min | RabbitMQ 消费者刷新，缓存缺失时同步兜底刷新 |
| 排行榜胜场榜 | `debateai:leaderboard:wins` | 10 min | RabbitMQ 消费者刷新，缓存缺失时同步兜底刷新 |
| 排行榜辩论数榜 | `debateai:leaderboard:debates` | 10 min | RabbitMQ 消费者刷新，缓存缺失时同步兜底刷新 |

### 2.5 RabbitMQ 队列

| 交换机 | 队列 | Routing Key | 生产者 | 消费者 | 用途 |
|------|------|-------------|--------|--------|------|
| `debateai.debate.exchange` | `debateai.debate.closing.queue` | `debate.closing.generate` | 手动结束辩论、达到最大轮次 | `DebateClosingConsumer` | 异步生成 AI 结辩并结束辩论 |
| `debateai.leaderboard.exchange` | `debateai.leaderboard.refresh.queue` | `leaderboard.refresh` | `LeaderboardRefreshScheduler` | `LeaderboardRefreshConsumer` | 异步重算排行榜并写入 Redis |

处理要求：

- AI 结辩消息在主事务提交后投递，避免数据库回滚后消费者提前处理。
- AI 结辩消费者只处理 `status = ending` 的辩论；成功后更新占位消息为 `complete`，失败后更新为 `failed`。
- 排行榜定时器默认每 5 分钟投递刷新消息，消费者重算 `points`、`wins`、`debates` 三个快照。
- 通知不经过 RabbitMQ；评论、投票、举报、管理员处理直接同步写入 `notifications` 表。

### 2.6 技术栈

| 层 | 选型 | 版本 | 备注 |
|----|------|------|------|
| 前端框架 | Nuxt 3 (Vue 3) | ^3.x | 仅作为 Web 前端，不使用 Nitro 承载业务 API |
| 前端 UI | Tailwind CSS + Nuxt UI | v3 | 统一组件风格 |
| 前端状态 | Pinia | ^2.x | 前端会话与辩论状态 |
| 后端语言 | Java | 17+ | 推荐 Java 17 或 21 LTS |
| 后端框架 | Spring Boot | 3.x | REST API、流式响应、任务调度 |
| AI 框架 | Spring AI | 1.x 稳定线优先 | ChatClient、流式输出、模型抽象，DeepSeek 可通过 OpenAI 兼容接口接入 |
| 安全 | Spring Security + JWT | - | JWT 需包含 `jti` |
| 数据访问 | MyBatis-Plus | - | CRUD、分页、Mapper |
| 数据库迁移 | Flyway，可选 | - | 生产推荐；MVP 可先用 `schema.sql` 或手动 SQL，后续再接入 |
| 数据库 | MySQL | 8.0+ | 推荐托管或独立实例 |
| 缓存 | Redis + Spring Data Redis | 7.x | 推荐 Upstash/Redis Cloud/自建 |
| 队列 | RabbitMQ + Spring AMQP | 3.x | 当前与 API 同进程消费，后续可拆分独立 worker |
| AI HTTP 客户端 | Spring AI ChatClient + WebClient 兜底 | - | 优先 Spring AI，必要时对 DeepSeek 做兼容适配 |
| 参数校验 | Jakarta Validation | - | DTO 字段校验 |
| 日志 | SLF4J + Logback | - | 结构化日志可接入 ELK/云日志 |
| 部署 | 前端静态站点 + Java API | - | 当前消费者和定时任务在 Java API 内运行，后续可拆分 worker |

### 2.7 部署说明

本项目采用前后端分离部署。前端构建为静态资源或 Nuxt Node 服务，后端以 Java Spring Boot 常驻服务运行。系统包含长连接流式输出、数据库连接池、RabbitMQ 消费者、Redis 连接和定时任务，纯 Serverless 架构不适合作为主部署方案。

| 组件 | 推荐部署 | 原因 |
|------|----------|------|
| Frontend Web | 静态托管、Nginx、对象存储 CDN 或 Nuxt Node 服务 | 独立发布前端资源 |
| Java API | Spring Boot 常驻服务 | 承载 REST API、认证、数据库事务、AI 流式接口、RabbitMQ 消费者和定时任务 |
| Java Worker | 可选独立 Spring Boot Worker 进程 | 后续流量上来后，可把 AI 结辩和排行榜刷新消费者拆出 |
| MySQL | 托管 MySQL 或独立云主机 | 稳定连接与备份 |
| Redis | 托管 Redis 或独立云主机 | 限流、缓存、排行榜 |
| RabbitMQ | 托管 MQ 或独立云主机 | 需要持久化队列和 DLQ |
| 管理后台 | 同一前端应用内的 admin 路由 | MVP 阶段降低维护成本，权限由 Java API 校验 |

部署建议：

- 使用 Docker Compose、Kubernetes、systemd 或进程管理平台运行 Frontend 和 Java API；如果后续拆分消费者，再单独运行 Java Worker。
- 当前可先让 API 与消费者同进程运行；生产流量上来后再拆分独立 worker，避免 AI 流式请求影响异步任务消费。
- Spring Boot 使用 HikariCP 连接池，并设置连接数上限。
- 前端通过 `NUXT_PUBLIC_API_BASE` 或同源反向代理访问 Java API。
- 生产环境必须配置日志、健康检查和进程自动重启。

Flyway 说明：

- Flyway 是数据库版本迁移工具，用来把建表、加字段、加索引等 SQL 变更按版本顺序自动执行。
- 它解决的是“不同环境数据库结构如何保持一致”的问题，例如本地、测试、生产都能按 `V1__init.sql`、`V2__add_index.sql` 顺序演进。
- MVP 可以不用 Flyway，先把建表 SQL 放在 `backend/src/main/resources/schema.sql` 或 `docs/sql/` 中手动执行。
- 一旦多人协作或准备上线，建议再启用 Flyway，避免手工改库造成环境不一致。

### 2.8 目录结构

```text
debate-ai/
├── frontend/
│   ├── pages/
│   │   ├── index.vue
│   │   ├── login.vue
│   │   ├── register.vue
│   │   ├── debate/
│   │   │   ├── index.vue
│   │   │   ├── new.vue
│   │   │   └── [id].vue
│   │   ├── topic/
│   │   │   └── index.vue
│   │   ├── profile/
│   │   │   └── [username].vue
│   │   └── admin/
│   │       ├── index.vue
│   │       ├── models.vue
│   │       ├── reports.vue
│   │       └── users.vue
│   ├── components/
│   │   ├── debate/
│   │   ├── topic/
│   │   ├── layout/
│   │   └── shared/
│   ├── composables/
│   │   ├── useApi.ts
│   │   ├── useAuth.ts
│   │   ├── useDebate.ts
│   │   └── useStream.ts
│   ├── stores/
│   │   ├── auth.store.ts
│   │   └── debate.store.ts
│   ├── nuxt.config.ts
│   ├── package.json
│   └── .env.example
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/debateai/
│       │   │   ├── DebateAiApplication.java
│       │   │   ├── controller/       (Auth | Debate | Topic | Vote | Comment | Admin | Report | User | Notification | Leaderboard)
│       │   │   ├── service/
│       │   │   │   ├── ai/           (AiProvider | DebateContext | SpringAiDebateProvider)
│       │   │   │   ├── AuthService.java
│       │   │   │   ├── DebateService.java
│       │   │   │   ├── VoteService.java
│       │   │   │   ├── NotificationService.java
│       │   │   │   ├── LeaderboardService.java
│       │   │   │   ├── DebateClosingConsumer.java
│       │   │   │   └── LeaderboardRefreshConsumer.java
│       │   │   ├── mapper/           (MyBatis Mapper)
│       │   │   ├── entity/           (数据库实体)
│       │   │   ├── dto/              (Request/Response DTO)
│       │   │   ├── security/         (JWT Filter | SecurityConfig)
│       │   │   ├── config/           (Security | CORS | AsyncQueue)
│       │   │   └── common/           (统一响应、异常、日志、分页)
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── schema.sql
│       │       └── upgrade.sql
│       └── test/
│           └── java/com/debateai/
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## 三、数据库设计

### 3.1 ER 关系

```text
User ──1:N──► Debate ──1:N──► Message
  │               │
  │               ├──N:1──► Topic
  │               ├──1:N──► Vote
  │               └──1:N──► Comment
  │
  ├──1:N──► Topic (creator)
  ├──1:N──► Report
  └──1:N──► Notification
```

### 3.2 核心表

**users**

| 字段 | 类型 | 约束 |
|------|------|------|
| id | INT UNSIGNED AUTO_INCREMENT | PK |
| username | VARCHAR(30) | UNIQUE, NOT NULL |
| email | VARCHAR(100) | UNIQUE, NOT NULL |
| password_hash | VARCHAR(255) | NOT NULL |
| avatar_url | VARCHAR(500) | NULL |
| bio | VARCHAR(200) | NULL |
| role | ENUM('user','admin') | DEFAULT 'user' |
| status | ENUM('active','disabled') | DEFAULT 'active' |
| points | INT UNSIGNED | DEFAULT 0 |
| wins | INT UNSIGNED | DEFAULT 0 |
| losses | INT UNSIGNED | DEFAULT 0 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP |
| updated_at | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |
| deleted_at | DATETIME | NULL |

索引：`UNIQUE(username)`、`UNIQUE(email)`、`INDEX(status)`、`INDEX(points)`。

**topics**

| 字段 | 类型 | 约束 |
|------|------|------|
| id | INT UNSIGNED AUTO_INCREMENT | PK |
| title | VARCHAR(100) | NOT NULL |
| description | TEXT | NULL |
| category | ENUM('科技','社会','哲学','教育','娱乐','其他') | NOT NULL |
| creator_id | INT UNSIGNED | FK -> users.id |
| status | ENUM('visible','hidden') | DEFAULT 'visible' |
| debate_count | INT UNSIGNED | DEFAULT 0 |
| view_count | INT UNSIGNED | DEFAULT 0 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP |
| updated_at | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |
| deleted_at | DATETIME | NULL |

索引：`INDEX(category, created_at)`、`INDEX(creator_id)`、`INDEX(status)`。

**debates**

| 字段 | 类型 | 约束 |
|------|------|------|
| id | INT UNSIGNED AUTO_INCREMENT | PK |
| topic_id | INT UNSIGNED | FK -> topics.id |
| user_id | INT UNSIGNED | FK -> users.id |
| user_stance | ENUM('pro','con') | NOT NULL |
| ai_stance | ENUM('pro','con') | NOT NULL |
| ai_model | VARCHAR(60) | NOT NULL |
| style | ENUM('mild','intense') | DEFAULT 'mild' |
| visibility | ENUM('public','private') | DEFAULT 'public' |
| status | ENUM('active','ending','ended','failed') | DEFAULT 'active' |
| current_round | INT UNSIGNED | DEFAULT 1 |
| max_rounds | INT UNSIGNED | DEFAULT 5，业务层限制 5-15 |
| winner | ENUM('user','ai','draw') | NULL |
| vote_count_user | INT UNSIGNED | DEFAULT 0 |
| vote_count_ai | INT UNSIGNED | DEFAULT 0 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP |
| updated_at | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |
| ended_at | DATETIME | NULL |
| deleted_at | DATETIME | NULL |

索引：`INDEX(topic_id)`、`INDEX(user_id, created_at)`、`INDEX(status, visibility, created_at)`。

**messages**

| 字段 | 类型 | 约束 |
|------|------|------|
| id | INT UNSIGNED AUTO_INCREMENT | PK |
| debate_id | INT UNSIGNED | FK -> debates.id |
| role | ENUM('user','ai','system') | NOT NULL |
| content | TEXT | NOT NULL |
| round | INT UNSIGNED | NOT NULL |
| ai_provider | VARCHAR(30) | NULL |
| ai_model | VARCHAR(80) | NULL |
| latency_ms | INT UNSIGNED | NULL |
| status | ENUM('pending','complete','failed') | DEFAULT 'complete' |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP |

索引：`INDEX(debate_id, round)`。

**votes**

| 字段 | 类型 | 约束 |
|------|------|------|
| id | INT UNSIGNED AUTO_INCREMENT | PK |
| debate_id | INT UNSIGNED | FK -> debates.id |
| voter_id | INT UNSIGNED | FK -> users.id |
| voted_for | ENUM('user','ai') | NOT NULL |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP |
| updated_at | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

约束：`UNIQUE(debate_id, voter_id)`。MVP 允许改票，通过更新 `voted_for` 实现。

**comments**

| 字段 | 类型 | 约束 |
|------|------|------|
| id | INT UNSIGNED AUTO_INCREMENT | PK |
| debate_id | INT UNSIGNED | FK -> debates.id |
| user_id | INT UNSIGNED | FK -> users.id |
| content | TEXT | NOT NULL |
| status | ENUM('visible','hidden') | DEFAULT 'visible' |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP |
| updated_at | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |
| deleted_at | DATETIME | NULL |

索引：`INDEX(debate_id, created_at)`、`INDEX(user_id, created_at)`、`INDEX(status)`。

### 3.3 治理与异步表

**reports**

| 字段 | 类型 | 约束 |
|------|------|------|
| id | INT UNSIGNED AUTO_INCREMENT | PK |
| reporter_id | INT UNSIGNED | FK -> users.id |
| target_type | ENUM('topic','debate','comment','user') | NOT NULL |
| target_id | INT UNSIGNED | NOT NULL |
| reason | VARCHAR(200) | NOT NULL |
| status | ENUM('pending','resolved','rejected') | DEFAULT 'pending' |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP |

**notifications**

| 字段 | 类型 | 约束 |
|------|------|------|
| id | INT UNSIGNED AUTO_INCREMENT | PK |
| user_id | INT UNSIGNED | FK -> users.id |
| type | ENUM('comment','vote','report_created','report_handled','system') | DEFAULT 'system' |
| title | VARCHAR(80) | NOT NULL |
| content | VARCHAR(500) | NULL |
| link_url | VARCHAR(255) | NULL |
| status | ENUM('unread','read') | DEFAULT 'unread' |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP |
| read_at | DATETIME | NULL |
| deleted_at | DATETIME | NULL |

当前实现没有 `outbox_events` 表。AI 结辩消息在事务提交后投递 RabbitMQ；通知直接同步写 MySQL。

**AI 模型配置**

当前阶段不建立 `ai_models` 表。后端通过 `application-local.yml` 或环境变量指定单个 OpenAI 兼容模型，例如 `deepseek-chat`；需要更换模型时由开发者修改配置并重启后端。

---

## 四、API 接口设计

### 4.1 接口总览（23 个）

#### 认证（4）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| POST | `/api/auth/register` | 注册 | - |
| POST | `/api/auth/login` | 登录 | - |
| POST | `/api/auth/logout` | 登出并拉黑当前 JWT | yes |
| GET | `/api/auth/me` | 当前用户 | yes |

#### 辩论（5）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/debate` | 辩论列表 | - |
| POST | `/api/debate` | 创建辩论 | yes |
| GET | `/api/debate/{id}` | 辩论详情 | - |
| POST | `/api/debate/{id}/message` | 发言并流式返回 AI 回复 | yes |
| POST | `/api/debate/{id}/end` | 结束辩论 | yes |

#### 话题（3）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/topic` | 话题列表 | - |
| POST | `/api/topic` | 创建话题 | yes |
| GET | `/api/topic/{id}` | 话题详情 | - |

#### 投票 / 评论（4）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| POST | `/api/vote` | 投票或改票 | yes |
| GET | `/api/comment` | 评论列表 | - |
| POST | `/api/comment` | 发评论 | yes |
| POST | `/api/comment/{id}/hide` | 隐藏评论 | admin |

#### 用户 / 治理（2）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/user/{username}` | 用户主页 | - |
| POST | `/api/report` | 举报内容 | yes |

#### 管理后台（2）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|:--:|
| GET | `/api/admin/reports` | 举报列表 | admin |
| PATCH | `/api/admin/users/{id}` | 禁用或恢复用户 | admin |

### 4.2 通用规范

**认证**

- `Authorization: Bearer <token>`。
- JWT payload 至少包含 `sub`、`jti`、`role`、`iat`、`exp`。
- 登出时将 `jti` 写入 Redis 黑名单。

**分页**

```text
page: number = 1
size: number = 20, max = 50
sort: created_at | hot | votes
order: asc | desc
```

**统一响应**

```json
{
  "success": true,
  "data": {}
}
```

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "参数不合法",
    "details": {}
  }
}
```

```json
{
  "success": true,
  "data": {
    "list": [],
    "total": 0,
    "page": 1,
    "size": 20
  }
}
```

**错误码**

`VALIDATION_ERROR` `UNAUTHORIZED` `FORBIDDEN` `NOT_FOUND` `CONFLICT` `AI_TIMEOUT` `AI_ERROR` `RATE_LIMIT` `CONTENT_BLOCKED`

### 4.3 核心接口契约

**POST /api/debate**

```json
{
  "topicId": 1,
  "userStance": "pro",
  "style": "mild",
  "visibility": "public",
  "maxRounds": 5
}
```

```json
{
  "debate_id": 1001,
  "ai_first_message": {
    "id": 2001,
    "content": "AI 开篇立论内容",
    "round": 1
  }
}
```

流程：校验话题和用户状态 -> 规范化 `userStance/style/visibility/maxRounds` -> 创建 Debate -> 调用 AI 开篇立论 -> 保存 AI 消息 -> 返回结果。`maxRounds` 可不传，默认 5，低于 5 按 5，高于 15 按 15。

**POST /api/debate/{id}/message**

```json
{
  "content": "用户本轮发言"
}
```

校验：

- 仅辩论创建者可发言。
- 辩论状态必须为 `active`。
- `content` 长度 1-2000 字。
- 单用户同一辩论同一时间只允许一个 AI 回复生成中。

响应：返回流式数据，见 2.3。

**POST /api/debate/{id}/end**

```json
{
  "debateId": 1001,
  "status": "ending",
  "winner": null,
  "aiClosingMessage": {
    "id": 2008,
    "content": "辩论已结束，AI 结辩正在生成中，请稍后刷新查看。",
    "round": 6,
    "status": "pending"
  },
  "endedAt": null
}
```

流程：状态置为 `ending` -> 插入 `pending` AI 结辩消息 -> 事务提交后投递 `debate.closing.generate` -> 消费者生成结辩 -> 消息置为 `complete` -> 辩论状态置为 `ended` 并写入 `ended_at`。生成失败时消息置为 `failed`，辩论状态置为 `failed`。

**POST /api/vote**

```json
{
  "debate_id": 1001,
  "voted_for": "user"
}
```

规则：

- 登录用户可投票。
- 只能对 `status = ended` 的辩论投票。
- 创建者不能参与自己辩论的投票。
- 私密辩论不可被非创建者查看或投票。
- MVP 允许改票。
- 投票写库后异步刷新汇总计数；接口可返回当前用户投票状态。

---

## 五、前端设计

### 5.1 路由

| 路由 | 页面 | 组件 |
|------|------|------|
| `/` | 社区广场 | TopicCard + Leaderboard + DebateCard |
| `/login` | 登录 | LoginForm |
| `/register` | 注册 | RegisterForm |
| `/debate` | 辩论大厅 | DebateCard + Filters + Pagination |
| `/debate/new` | 创建辩论 | TopicPicker + StanceSelector + Settings |
| `/debate/[id]` | 辩论房间 | DebateChat + VotePanel + CommentList |
| `/topic` | 话题广场 | TopicCard + CategoryTabs |
| `/profile/[username]` | 个人主页 | ProfileHeader + Stats + DebateHistory |
| `/admin` | 管理概览 | AdminStats + PendingReports |
| `/admin/reports` | 举报处理 | ReportTable + ModerationActions |
| `/admin/users` | 用户管理 | UserTable + UserStatusActions |

### 5.2 状态管理

```typescript
// auth.store
// user, token, isAuthenticated, login(), register(), logout(), refreshMe()

// debate.store
// currentDebate, messages, streamStatus, sendMessage(), cancelMessage(), endDebate()
```

`streamStatus` 建议枚举：

```typescript
type StreamStatus = 'idle' | 'connecting' | 'streaming' | 'done' | 'error' | 'cancelled'
```

### 5.3 Composables

```typescript
// useAuth() -> { user, login, register, logout, requireAuth }
// useDebate() -> { messages, sendMessage, cancelMessage, endDebate, streamStatus }
// useStream() -> { start(request), cancel(), onToken, onDone, onError }
```

### 5.4 关键交互流

**发送辩论消息**

```text
用户输入
  -> 前端校验空内容和长度
  -> 本地乐观渲染用户消息
  -> POST /api/debate/{id}/message
  -> fetch ReadableStream 读取 token
  -> token 逐段追加到 AI 消息
  -> done 后固化消息
  -> error 时保留用户消息并展示重试入口
```

**异常状态**

| 场景 | 前端行为 |
|------|----------|
| AI 超时 | 停止流式读取，显示重试 |
| 用户离开页面 | AbortController 取消请求 |
| 登录失效 | 清空 token，跳转登录页 |
| 评论发送失败 | 保留草稿，允许重试 |
| 投票失败 | 回滚本地投票状态 |

---

## 六、AI 辩论引擎

### 6.1 Spring AI 与 Provider 接口

后端优先通过 Spring AI 的 `ChatClient` 实现模型调用和流式输出。DeepSeek 可以按 OpenAI 兼容接口配置 `base-url` 与 `api-key` 接入；如果某个模型能力 Spring AI 暂未覆盖，再在 `AiProvider` 中用 `WebClient` 做兜底适配。

```java
public interface AiProvider {
    String name();

    Flux<String> debate(DebateContext context);

    String openingStatement(DebateContext context);

    String closingStatement(DebateContext context);
}
```

`DebateContext` 建议包含 `topic`、`userStance`、`aiStance`、`style`、`round`、`history` 和 `summary`。如果后端不引入 WebFlux，也可以将 `Flux<String>` 替换为 `Iterator<String>` 或自定义回调式流接口，由 Controller 写入 `StreamingResponseBody`。

模型选择规则：

- 当前只使用配置文件或环境变量中的一个模型。
- 默认本地开发配置为 DeepSeek 的 OpenAI 兼容接口和 `deepseek-chat` 模型。
- 如需更换模型，修改 `spring.ai.openai.base-url`、`spring.ai.openai.api-key` 和 `spring.ai.openai.chat.options.model` 后重启后端。

### 6.2 Prompt 规则

- 立场锁定：AI 不能改变立场。
- 轮次策略：前 3 轮立论，4-8 轮攻防，9 轮后收束。
- 长度控制：每轮 200-400 字。
- 风格切换：`mild` 先肯定后反驳，`intense` 更直接但不能攻击人格。
- 内容约束：禁止人身攻击、仇恨内容、违法指导；需要围绕论点和证据。

### 6.3 上下文管理

- MVP 必须上线历史摘要能力，避免长辩论超出模型上下文。
- 默认保留最近 20 条消息，而不是 20 轮。
- 超过限制后保留开篇消息、最近 10 条消息，并生成一段历史摘要写入 `system` 消息或独立摘要字段。
- 每次请求估算 token，超过模型上限时优先压缩历史。
- 每轮回复 `max_tokens=800`，超时 30 秒。

### 6.4 故障切换

```text
首选 Provider 调用失败
  -> 记录错误和耗时
  -> 切换备用 Provider
  -> 备用成功：继续流式输出并标记 ai_provider
  -> 备用失败：返回 AI_ERROR
```

切换限制：

- 同一次消息最多切换 1 次。
- 超时、429、5xx 可切换；内容安全拦截不切换。
- 失败消息必须落库，便于排查。

### 6.5 环境变量

```env
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/debate_ai?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=pass
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_RABBITMQ_HOST=localhost
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest
DEEPSEEK_API_KEY=sk-xxxx
OPENAI_API_KEY=sk-xxxx
DEFAULT_AI_PROVIDER=deepseek
DEFAULT_AI_MODEL=deepseek-chat
SPRING_AI_OPENAI_API_KEY=${DEEPSEEK_API_KEY}
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com
SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=deepseek-chat
JWT_SECRET=change-me
JWT_EXPIRES_SECONDS=604800
FRONTEND_BASE_URL=http://localhost:3000
NUXT_PUBLIC_API_BASE=http://localhost:8080/api
```

---

## 七、安全、治理与风控

### 7.1 基础安全

- 密码使用 BCrypt 哈希，禁止明文存储。
- JWT 必须设置过期时间，并使用 `jti` 支持登出和封禁。
- 所有写接口都需要限流。
- 用户输入需要做长度校验、HTML 转义和内容安全检查。
- 私密辩论仅创建者可查看。

### 7.2 内容治理

| 能力 | MVP 要求 |
|------|----------|
| 举报 | 登录用户可举报话题、辩论、评论、用户 |
| 管理员页面 | MVP 必须提供简单后台处理举报、隐藏内容、禁用用户 |
| 隐藏 | 管理员可隐藏评论、话题或辩论 |
| 用户禁用 | 管理员可禁用用户登录和写入 |
| 模型配置 | 当前阶段由配置文件或环境变量指定单个模型，不提供管理员模型管理后台 |
| AI 输出安全 | 命中安全策略时返回 `CONTENT_BLOCKED` |

### 7.3 投票与积分规则

- 只有辩论结束后才允许投票。
- 辩论结束后才进入胜负结算。
- 默认按投票数判定胜负：用户票多则 user 胜，AI 票多则 ai 胜，相等为 draw。
- 创建者不能参与自己辩论的投票。
- 每个用户每场辩论一票，MVP 允许改票。
- 积分建议：胜利 +10，平局 +3，失败 +0；具体数值可配置。
- Worker 根据 `debate_id` 幂等结算，防止重复加分。

---

## 八、开发计划

### 阶段规划

| Phase | 目标 | 预计工期 | 验收标准 |
|-------|------|:--:|----------|
| 1 | 项目地基：脚手架、数据库、认证 | 2-3 天 | 能注册、登录、获取当前用户 |
| 2 | AI 核心：Provider、模型配置、历史摘要、流式回复、辩论房间 | 4-5 天 | 能创建辩论并完成长上下文流式 AI 对话 |
| 3 | 社区功能：话题、围观、结束后投票、评论 | 3-4 天 | 能围观、在辩论结束后投票、评论、查看列表 |
| 4 | 异步与治理：MQ 结辩、排行榜快照、通知、举报、管理后台 | 3-4 天 | AI 结辩和排行榜可异步处理，管理员可处理举报 |
| 5 | UI 打磨：响应式、暗色、异常状态 | 2-3 天 | 主要页面移动端可用 |
| 6 | 测试与部署：E2E、环境配置、上线 | 2-3 天 | 部署环境可稳定完成核心链路 |

### Phase 1 详细

1.1 创建 `frontend/` Nuxt 3 脚手架  
1.2 创建 `backend/` Spring Boot 3 脚手架  
1.3 Tailwind + Nuxt UI  
1.4 初始化 MySQL 表结构：MVP 可先用 `schema.sql`，上线前建议接入 Flyway  
1.5 MyBatis-Plus 实体、Mapper 和基础 Service  
1.6 Redis 模块与限流基础能力  
1.7 Spring Security、JWT Filter 和黑名单  
1.8 认证 API  
1.9 登录/注册页面  
1.10 AppLayout

### Phase 2 详细

2.1 Spring AI ChatClient 接入  
2.2 Java AI Provider 接口  
2.3 DeepSeek Provider  
2.4 OpenAI Provider  
2.5 单模型配置和默认 DeepSeek 模型  
2.6 历史摘要能力  
2.7 故障切换  
2.8 创建辩论 API  
2.9 流式发言 API  
2.10 结束辩论 API  
2.11 辩论详情 API  
2.12 DebateChat  
2.13 `useStream`

### Phase 3 详细

3.1 话题 API  
3.2 话题广场页  
3.3 辩论列表 API  
3.4 辩论大厅页  
3.5 结束后投票 API  
3.6 评论 API  
3.7 VotePanel  
3.8 评论区  
3.9 用户主页 API  
3.10 个人主页

### Phase 4 详细

4.1 Spring AMQP 连接和队列声明  
4.2 AI 结辩队列与 `DebateClosingConsumer`  
4.3 排行榜刷新队列、定时投递与 Redis 快照  
4.4 通知同步写库  
4.5 举报 API  
4.6 管理员后台页面  
4.7 举报处理、隐藏内容和用户禁用

### Phase 5 详细

5.1 移动端适配  
5.2 暗色模式  
5.3 空状态、错误状态、加载状态  
5.4 Toast 通知  
5.5 表单校验  
5.6 基础可访问性

### Phase 6 详细

6.1 单元测试  
6.2 API 集成测试  
6.3 流式接口测试  
6.4 核心 E2E 测试  
6.5 生产环境变量  
6.6 部署脚本  
6.7 README

### 依赖包

| 分类 | 依赖 | 用途 |
|------|------|------|
| 前端 | `nuxt`, `vue` | 前端框架 |
| 前端 | `@nuxt/ui`, `tailwindcss` | UI |
| 前端 | `@pinia/nuxt`, `pinia` | 状态管理 |
| 前端 | `zod` | 前端表单和响应校验 |
| 后端 | `spring-boot-starter-web` 或 `spring-boot-starter-webflux` | REST API 与流式响应 |
| 后端 | `spring-ai-starter-model-openai` | Spring AI ChatClient，DeepSeek 可通过 OpenAI 兼容接口接入 |
| 后端 | `spring-boot-starter-security` | 认证与权限 |
| 后端 | `mybatis-plus-spring-boot3-starter` | ORM/Mapper |
| 后端可选 | `flyway-core`, `flyway-mysql` | 数据库迁移，MVP 可暂不接入 |
| 后端 | `mysql-connector-j` | MySQL 驱动 |
| 后端 | `spring-boot-starter-data-redis` | Redis |
| 后端 | `spring-boot-starter-amqp` | RabbitMQ |
| 后端 | `jjwt-api`, `jjwt-impl`, `jjwt-jackson` | JWT |
| 后端 | `spring-boot-starter-validation` | 请求参数校验 |
| 后端 | `lombok` | 减少样板代码，可选 |

---

## 九、已确认决策

1. 架构采用前后端分离：Nuxt/Vue 前端独立部署，Java Spring Boot 后端独立部署。
2. 默认 AI 模型优先使用 DeepSeek，管理员后续可在后台新增、停用或调整模型优先级。
3. 投票只允许在辩论结束后进行。
4. MVP 需要简单管理员页面，用于举报处理、内容隐藏和用户禁用；模型暂时通过配置文件或环境变量切换。
5. MVP 需要上线对话历史摘要能力。
