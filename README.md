# AI Prompt System

一个基于 `Spring Boot 3` + `Java 21` 的 Prompt 管理与优化后端项目。

它的目标不是只做一个“调用一次大模型”的 Demo，而是把 Prompt 的创建、审核、查询、互动计数、热度排行，以及 AI 优化工作流串成一套完整的后端系统。

## 项目定位

这个项目更适合定义为：

- Prompt 内容平台后端
- AI Prompt 优化工作流后端
- 带有 Redis + RabbitMQ + 审核链路的工程化 Spring Boot 项目

它不是通用型 Agent 框架，但已经具备比较完整的 AI 工作流编排能力。

## 核心能力

- 用户注册、登录、JWT 鉴权、个人信息维护
- Prompt 的创建、更新、删除、详情、分页查询
- Prompt 分类管理
- 点赞、收藏、复制、浏览统计
- Redis 热门排行与搜索热词
- 搜索历史记录
- Prompt 发布审核链
- Prompt 优化审核链
- AI Prompt 分析、优化、复核
- SSE 流式返回优化结果
- 优化记录落库，并支持确认保存为正式 Prompt

## 技术栈

- `Java 21`
- `Spring Boot 3.5.x`
- `Spring Web`
- `Spring Security`
- `Spring AI`
- `MyBatis-Plus`
- `MySQL`
- `Redis`
- `RabbitMQ`
- `Ollama`
- `Knife4j / OpenAPI`
- `JUnit 5 + Mockito`

## 项目结构

```text
src/main/java/com/jojo/prompt
├─ common
│  ├─ config        # Spring / Redis / RabbitMQ / Security / AI 配置
│  ├─ constant      # 状态、Redis Key、MQ 常量
│  ├─ event         # 领域事件
│  ├─ exception     # 统一异常处理
│  ├─ filter        # JWT 过滤器
│  ├─ handler       # Prompt 审核责任链
│  ├─ listener      # 事件监听器
│  └─ mq            # MQ 消息、生产者、消费者
├─ controller       # REST API
├─ dto              # 请求 / 响应对象
├─ entity           # 数据库实体
├─ mapper           # MyBatis-Plus Mapper
├─ service
│  ├─ agent         # AI 分析 / 优化 / 复核
│  └─ impl          # 业务实现
└─ converter        # Entity / VO 转换
```

## 主要业务模块

### 1. 用户与鉴权

- 注册、登录、获取当前用户、更新资料、修改密码
- 使用 `JWT` 进行无状态认证
- 登录有限流与失败锁定逻辑

公开接口主要包括：

- `POST /api/user/register`
- `POST /api/user/login`

### 2. Prompt 内容管理

- 创建 Prompt 后先进入 `REVIEWING`
- 更新 Prompt 时使用版本号控制，避免并发覆盖
- 支持公开查询、个人查询、分类筛选、标签筛选、全文搜索
- 支持复制 Prompt 内容，并记录复制次数

代表性接口：

- `POST /api/prompts`
- `PUT /api/prompts`
- `DELETE /api/prompts/{id}`
- `GET /api/prompts/{id}`
- `GET /api/prompts/page`
- `GET /api/prompts/mine/page`
- `POST /api/prompts/{id}/copy`

### 3. 点赞、收藏、搜索

- 点赞 / 取消点赞
- 收藏 / 取消收藏
- 查询我的收藏
- 搜索历史
- 热门搜索词

代表性接口：

- `POST /api/prompts/{id}/like`
- `DELETE /api/prompts/{id}/like`
- `POST /api/prompts/{id}/favorite`
- `DELETE /api/prompts/{id}/favorite`
- `GET /api/prompts/myFavorites`
- `GET /api/search/history`
- `DELETE /api/search/history`
- `GET /api/search/hot`

### 4. 分类管理

- 分类增删改查
- 分类列表及分类下 Prompt 数量

代表性接口：

- `POST /api/categories`
- `PUT /api/categories`
- `DELETE /api/categories/{id}`
- `GET /api/categories/{id}`
- `GET /api/categories/list`
- `GET /api/categories/list-with-count`

## AI 优化工作流

这是项目里最有辨识度的一部分。

### 同步优化链路

1. 前端提交原始 Prompt、模板 ID、优化目标、输出格式
2. 先经过 Prompt 优化审核链
3. `PromptAnalyzeAgent` 生成问题分析结果
4. `PromptOptimizeAgent` 基于分析结果生成优化后的 Prompt
5. `PromptReviewAgent` 对优化结果做二次复核并输出结构化评分
6. 将优化记录、风险等级、评分、审核步骤落库
7. 用户可将优化结果确认保存为正式 Prompt

对应接口：

- `POST /api/prompt-optimizations`
- `GET /api/prompt-optimizations/{id}`
- `POST /api/prompt-optimizations/confirm`

### 流式优化链路

项目还支持通过 `SSE` 流式返回优化结果。

返回阶段通常包括：

- `REVIEW`
- `ANALYSIS`
- `OPTIMIZING`
- `TOKEN`
- `DONE`

对应接口：

- `POST /api/prompt-optimizations/stream`

示例请求文件保留在：

- `examples/prompt-optimization-stream.http`

## 审核与异步链路

### Prompt 发布审核链

Prompt 创建或更新后不会直接发布，而是：

1. 写入数据库，状态设为 `REVIEWING`
2. 事务提交后发送 RabbitMQ 审核消息
3. 消费端执行责任链审核
4. 通过则更新为 `ENABLED`
5. 拒绝则更新为 `REJECTED`

当前审核链包含：

- 敏感词检查
- 质量检查
- 原创性检查

### Prompt 优化审核链

在进入 AI 优化前，先做基础审核：

- 原始 Prompt 非空
- 模板可用
- 敏感词检查
- 结构完整度检查

这样可以把明显不合格的请求挡在模型调用之前。

## Redis 与计数设计

项目对浏览、点赞、收藏、复制这类高频操作做了 Redis 化处理。

- 实时计数先写 Redis
- 热度排行用 `ZSet`
- 搜索词热度也写 Redis
- 查询详情时合并 Redis 中的实时计数
- 再通过 RabbitMQ 延迟消息异步回刷数据库

这样做的目的：

- 减少数据库写压力
- 避免热点内容频繁直写数据库
- 提高排行榜和详情页读取性能

## RabbitMQ 设计

项目使用 RabbitMQ 处理两类异步任务：

- Prompt 审核
- Prompt 互动计数异步同步

其中计数同步使用“延迟队列 + 死信转发”的方式，把短时间内的多次互动合并后再落库。

## 安全与限流

项目已经实现了几类基础防护：

- JWT 鉴权
- 登录频率限制
- 登录失败锁定
- 搜索接口限流
- Prompt 复制去重计数

## 本地运行要求

运行前需要准备：

- `JDK 21`
- `Maven`
- `MySQL 8+`
- `Redis`
- `RabbitMQ`
- `Ollama`

`application-dev.yml` 中的默认依赖如下：

- MySQL: `localhost:3306/ai_prompt`
- Redis: `localhost:6379`
- RabbitMQ: `localhost:5672`
- Ollama: `http://localhost:11434`
- 默认模型: `qwen3.5:9b`

可以通过环境变量覆盖部分配置：

- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `OLLAMA_BASE_URL`
- `OLLAMA_MODEL`
- `DRUID_USERNAME`
- `DRUID_PASSWORD`

## 启动方式

### 1. 配置基础设施

先确保以下服务可用：

- MySQL
- Redis
- RabbitMQ
- Ollama

### 2. 修改配置

按本地环境调整：

- `src/main/resources/application-dev.yml`

### 3. 启动项目

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

### 4. 查看接口文档

- Knife4j: `http://localhost:8080/doc.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`

## 测试

运行测试：

```bash
./mvnw test
```

当前仓库已包含一批单元测试，覆盖了：

- 审核责任链
- 事件监听器
- MQ 消费者
- 点赞 / 收藏服务
- Prompt 优化查询逻辑

## 当前仓库整理说明

本次已清理掉以下不应长期放在项目仓库里的内容：

- 本地 IDE 配置目录
- `target/` 构建产物
- `.m2-temp/` 本地 Maven 依赖缓存
- `jmeter.log`
- 空的压测运行目录
- Spring Boot 默认生成的 `HELP.md`
- 一个误暂存的 `PromptOptimizationStreamService.xml`

同时已将以下内容加入忽略规则：

- `.m2-temp/`
- `jmeter.log`
- `performance/jmeter/qps/runs/`

## 当前已知缺口

这个项目已经具备不错的实习项目完成度，但仍有一些明显缺口：

- 仓库中暂未提供数据库初始化 SQL / DDL
- 没有 `docker-compose`，本地依赖需要手动准备
- 没有前端页面，主要以接口和 SSE 示例演示
- 部分源码中的中文注释和 Swagger 文案存在编码问题
- Agent 能力更偏“固定工作流编排”，不是通用 Agent 框架

## 适合如何介绍这个项目

如果用于简历或面试，建议这样描述：

> 这是一个基于 Spring Boot 的 Prompt 内容平台后端，集成了 Redis、RabbitMQ 和 Spring AI，实现了 Prompt 发布审核、互动计数异步同步、热门排行，以及 Prompt 分析-优化-复核的 AI 工作流，并支持 SSE 流式返回优化结果。
