# 行迹应变 — 多人出行动态规划与中断恢复平台

## 项目概述
本项目覆盖“成员约束 → 初始计划 → 外部事件 → 影响分析 → 替代方案 → 集体确认 → 新计划 → 变更回退”的完整闭环，并在此之上提供好友/群聊、攻略社区、预算分账与首页智能体等协作能力。

- 后端：Spring Boot 3.3 + Spring Data JPA + Spring Security(JWT) + WebSocket/STOMP，默认连接 MySQL，测试使用 H2。
- 前端：React + TypeScript + Vite + TanStack Query + Tailwind，位于 `frontend/`。
- 文档：`docs/` 下为需求、设计、数据库、UML 与前后端对接说明；`docs/sql/` 为建表、种子与视图脚本。

## 目录结构
```
src/main/java/com/trip/adaptive
├── ai            AI 客户端与首页智能体（AssistantService）
├── config        安全、WebSocket、OpenAPI、数据种子
├── controller    /api REST 接口
├── domain        JPA 实体与枚举
├── monitor       事件采集、影响匹配、重规划、投票与变更记录
├── repository    数据访问层
└── service       行程、群组、社区、预算、聊天等业务服务
frontend/src
├── api           请求封装（api.client）
├── pages         各业务页面
├── layout        全局布局与实时消息桥接
└── components    UI 组件
docs/             需求 / 设计 / 数据库 / UML / SQL
```

## 如何运行
要求 Java 17、Maven 3.9+、Node 18+。

后端：
```bash
export DB_HOST=localhost DB_USER=root DB_PASSWORD=your-password
./mvnw spring-boot:run
```
前端：
```bash
cd frontend
npm ci
npm run dev      # 开发服务，默认代理到 http://localhost:8080
npm run build    # 生产构建
npm run lint
```
检查与测试：
```bash
./mvnw spotless:apply   # Google Java Format
./mvnw test
```

Swagger UI：`http://localhost:8080/swagger-ui.html`
健康检查：`http://localhost:8080/actuator/health`

## 主要能力

### 行程与小组生命周期
- `DELETE /api/trips/{id}`：删除行程，仅小组群主可操作；会级联清理节点、事件、影响评估、替代方案、投票、变更记录、费用与讨论。
- `POST /api/groups/{id}/leave`：成员主动退出小组，同时清理其成员约束与投票记录；群主需先转移群主或解散小组。
- `DELETE /api/groups/{id}`：群主解散小组，连同小组下的全部行程、群聊会话与成员数据一并删除。

### 全局旅行助手
- 入口是各页面右下角的悬浮图标（`AssistantDock`），任意页面都能唤起，`Esc` 关闭。
- `POST /api/ai/assistant`，请求体 `{"question": "上海三天适合去哪玩？"}`。
- 服务端先在攻略社区中按标题/城市/主题/标签/正文检索，再匹配站内功能目录，最后把检索结果作为上下文交给 DeepSeek 生成回答。
- 未配置 `DEEPSEEK_API_KEY` 或调用失败时，退回本地攻略与目录导航答案，响应中的 `source` 字段标明 `ai` / `local` / `offline`。
- 所有模型调用都在服务端完成，密钥不会下发到浏览器。

### 替代方案的轮次管理
- 重新生成方案时，上一轮的候选会被标记为归档（`AlternativePlan.archived`），未进入投票的 `PROPOSED` 方案直接删除，因此界面上只会出现当前这一轮的可选项。
- 某个方案通过后，同轮其它方案会被置为 `REJECTED` 并归档；已归档方案不能再发起投票。
- 每次监测生成的方案共用一个轮次号（`AlternativePlan.roundNo`）。`GET /api/trips/{id}/plans` 返回**当前轮次的全部方案**（含已否决、已采纳的），页面因此能完整列出这次监测给出的所有选择；`GET /api/trips/{id}/plans/history` 返回更早轮次，供“历史方案”折叠区展示。

### 可选择的替代地点
- `GET /api/plan-changes/{changeId}/candidates`：列出该节点变更全部通过校验（预算 → 可达半径 → 饮食 → 天气 → 事件 → 去重）的替代地点，候选来源为 AI 提名、地图就近搜索与其他队伍走过的同类节点，并附带距离、评分、评论数、室内与否与亮点标签。
- `PUT /api/plan-changes/{changeId}/replacement`：成员改选替代地点，请求体 `{"name": "...", "lat": 31.2, "lng": 121.4}`；服务端会重新跑一次校验（同名或 50 米内视为同一地点），只有方案仍是 `PROPOSED` 且未归档时才允许改选，改完会重算方案的额外成本与改动节点数。
- 候选数量上限由 `replan.selectable-candidate-count`（默认 8）控制。

### 节点级投票（这个节点换到哪里）
方案层的整轮投票（`/api/plans/{id}/votes`）决定是否采纳整套方案；节点级投票只决定某一条节点变更用哪个替代地点。

- `GET /api/plan-changes/{changeId}/node-votes`：当前票数分布、参与人数、弃权数、是否平票、每票备注。
- `POST /api/plan-changes/{changeId}/node-votes`：投票，请求体 `{"memberId": 1, "choice": "CANDIDATE|KEEP_PLAN|ABSTAIN", "placeName": "...", "lat": 31.2, "lng": 121.4, "comment": "可选备注"}`。
- `POST /api/plan-changes/{changeId}/node-votes/tally`：提前计票（例如群主想在全员投完前定案）。

规则：

- 每位成员对每条节点变更只有一票（`(node_change_id, member_id)` 唯一），可随时改票。
- `CANDIDATE` 的地点会用 `candidatesFor` 重新校验（同名或 50 米内视为同一地点），伪造坐标或过期候选会被拒绝。
- `ABSTAIN` 计入参与人数，但不计入任何选项；`KEEP_PLAN` 表示维持方案里给出的原安排，是一个独立的可投选项。
- 落定条件：某个选项获得**全体成员过半**支持，或全员表态完毕且存在唯一领先项；平票一律不落定，避免系统替成员做隐式选择。
- 落定为候选地点时才写回 `NodeChange` 并重算方案的额外成本与改动节点数；落定为 `KEEP_PLAN` 时不改动任何字段。
- 方案离开 `PROPOSED`（进入整轮投票、被采纳或归档）后，节点投票关闭。

### 外部数据缺失时的展示口径
- 天气实时接口（和风/weathercn）不可用、未配置 Key 或返回缺失温度时，`GET /api/weather/preview` 会按当地纬度与当月推算常年同期的白天温度区间与天气描述返回，并把 `source` 标为 `offline`，界面显示“本地天气参考”。
- 这样界面不会再出现 `0°C` 这类缺值渲染；实时服务恢复后自动切回 `source=live`。

### 事件监测的自清理
- 每次执行影响评估（`POST /api/trips/{id}/assess`）时，会先清除由监测器自动生成、但地点已不属于任何有效行程节点的事件（如换掉某个景点后残留的天气记录），再重算影响与风险。
- 前端在方案应用、回退、计票和重新评估后都会失效事件/影响/风险缓存，避免旧节点的天气卡片继续停留在界面上。

### 变更记录
- `ChangeLog` 增加 `type`（`APPLIED` / `REVERTED`）与 `details` 字段，不再依赖描述文案判断是否已回退。
- `details` 记录节点级别的前后对照（原地点、新地点、时间调整），前端按时间线展示，并只对仍处于已应用状态的记录提供回退入口。

### 管理端与数据看板
- 数据看板不在普通用户导航中出现；前端 `/admin` 路由由 `RequireAdmin` 保护。
- 后端 `GET /api/admin/stats` 会校验当前用户的管理员标识，非管理员返回 403。
- 管理员由配置项 `app.admin-emails`（环境变量 `ADMIN_EMAILS`，逗号分隔）指定，启动时由 `DataSeeder` 标记。

## 配置说明
| 配置项 | 环境变量 | 说明 |
| --- | --- | --- |
| `spring.datasource.*` | `DB_HOST` / `DB_USER` / `DB_PASSWORD` | MySQL 连接信息 |
| `app.admin-emails` | `ADMIN_EMAILS` | 管理员邮箱列表，逗号分隔 |
| `app.jwt.secret` | `JWT_SECRET` | JWT 签名密钥，生产必须覆盖 |
| `app.deepseek.*` | `DEEPSEEK_API_KEY` / `DEEPSEEK_HOST` / `DEEPSEEK_MODEL` | AI 能力（含首页智能体） |
| `app.qweather.*` | `QWEATHER_KEY` / `QWEATHER_HOST` | 天气事件采集 |
| `app.baidu.*` | `BAIDU_MAP_AK` / `BAIDU_JS_AK` | 地图与地点检索 |

数据库结构由 `ddl-auto: update` 维护；`docs/sql/schema.sql` 中同步了 `alternative_plan.archived`、`change_log.type/details`、`user.admin` 等新增列，升级已有环境时可参考。
