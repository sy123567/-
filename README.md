# 行迹应变 — 多人出行动态规划与中断恢复平台

## 项目概述
本项目是一个可运行的 Spring Boot 3.3 后端，覆盖“成员约束 → 初始计划 → 外部事件 → 影响分析 → 替代方案 → 集体确认 → 新计划”的完整闭环。默认使用 H2 内存数据库，启动后会自动创建演示用户、群组和约束。

## 架构与模块
- `domain`：用户、群组、约束、行程、节点、事件、影响、替代方案、投票与变更日志 JPA 实体。
- `repository`：Spring Data JPA 数据访问层。
- `service`：计划生成、预算/日期交集、事件模拟、Haversine 影响匹配、风险评分、重规划和投票应用。
- `controller`：`/api` REST API。
- `config`：WebSocket/STOMP、OpenAPI、数据种子。

## 如何运行
要求 Java 17、Maven 3.9+：
```bash
mvn spring-boot:run
# 或
./mvnw spring-boot:run
```
也可先 `mvn -DskipTests package`，再 `java -jar target/trip-adaptive-platform-0.0.1-SNAPSHOT.jar`。

代码格式由 Spotless + Google Java Format 统一维护：
```bash
mvn spotless:apply
mvn spotless:check
```

Swagger UI：`http://localhost:8080/swagger-ui.html`  
H2 Console：`http://localhost:8080/h2-console`（JDBC URL `jdbc:h2:mem:tripdb`，用户 `sa`，密码为空）  
健康检查：`http://localhost:8080/actuator/health`

## 核心流程 curl 示例
启动后先查看种子数据：
```bash
curl http://localhost:8080/api/users
curl http://localhost:8080/api/groups/1/members
```
生成初始计划（响应包含 `trip` 和 `explanation`，假设种子群组 ID 为 1）：
```bash
curl -X POST http://localhost:8080/api/groups/1/plan
```
模拟外部事件并评估：
```bash
curl -X POST http://localhost:8080/api/trips/1/events/mock
curl -X POST http://localhost:8080/api/trips/1/assess
curl http://localhost:8080/api/trips/1/risk
```
生成替代计划，读取第一个计划（假设 ID 为 1）并启动投票：
```bash
curl -X POST http://localhost:8080/api/trips/1/replan
curl http://localhost:8080/api/trips/1/plans
curl -X POST http://localhost:8080/api/plans/1/start-voting
```
分别使用成员 ID 1、2 投票并结算：
```bash
curl -X POST http://localhost:8080/api/plans/1/votes \
  -H 'Content-Type: application/json' \
  -d '{"memberId":1,"choice":"APPROVE","comment":"同意"}'
curl -X POST http://localhost:8080/api/plans/1/votes \
  -H 'Content-Type: application/json' \
  -d '{"memberId":2,"choice":"APPROVE","comment":"同意"}'
curl -X POST http://localhost:8080/api/plans/1/tally
curl http://localhost:8080/api/trips/1/changelogs
```

## 配置说明
默认端口 8080、H2 `jdbc:h2:mem:tripdb`、`ddl-auto=update`。`application.yml` 中保留了注释形式的 MySQL 配置示例。开发环境 CORS 允许 `/api/**` 与 WebSocket `/ws` 的任意来源。
