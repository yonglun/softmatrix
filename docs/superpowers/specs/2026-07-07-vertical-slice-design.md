# EAP 垂直切片(子项目 1)设计文档

**日期**:2026-07-07
**状态**:已与产品负责人逐节确认
**上游文档**:[Softmatrix Enterprise Agent Platform PRD V1.0](../../Softmatrix%20Enterprise%20Agent%20Platform%20PRD%20V1.0.md)

---

## 1. 背景与项目拆分

PRD 定义的 EAP MVP 包含 8 个模块,涉及身份认证、组织权限、Workspace、Agent 管理、Flowise 集成、Runtime、Dashboard 等多个相对独立的子系统,不适合一次性设计和实现。经讨论决定按**垂直切片优先**策略拆分:

- **子项目 1(本文档)**:端到端最窄切片 —— Keycloak 登录 → 门户看到 Agent → Chat 运行(经 Flowise)→ 看到结果。目的是在第一个子项目内暴露两个最大的集成风险(Keycloak、Flowise),并使团队始终持有可演示系统。
- **后续子项目**(每个走独立的 设计 → 计划 → 实现 循环,顺序可再调整):
  1. Flowise Designer 嵌入 + Agent 管理加深(分类/标签/状态/导入导出/发布停用)
  2. 组织管理 + 角色权限(RBAC)
  3. Workspace
  4. Agent Runtime(会话/日志/Token/耗时)
  5. Dashboard
  6. 企业 IAM 对接(Keycloak Broker,PRD 模式一)

## 2. 目标

用户经 Keycloak 登录 Portal,在 Agent 列表中看到已登记的 Agent,打开 Chat 窗口对话;消息经 Portal 后端转发给 Flowise 预置的 Chatflow(LLM 为 Azure OpenAI),回复流式显示在窗口中。全程用户不直接接触 Flowise。

## 3. 范围

### 3.1 切片内

- docker-compose 环境:Keycloak + Flowise + PostgreSQL
- Keycloak `softmatrix` realm,预置 2 个测试用户(admin / user),启动时自动导入
- Spring Boot BFF 后端:OIDC 登录、Session 管理、Agent CRUD API、Chat SSE 转发
- React + Ant Design 前端:登录跳转、Agent 列表页(含登记/编辑表单)、Chat 对话页
- Flowise 中手工预置一个 Azure OpenAI Chatflow,通过 Portal 表单登记

### 3.2 切片外(明确不做)

- 组织/部门/角色管理、RBAC —— 切片内登录即可见并操作所有 Agent
- Flowise Designer 嵌入、Workspace、Runtime 监控、Dashboard、审计日志
- 企业 IAM 对接(切片用 Keycloak 自带用户库,即 PRD 模式二)
- 对话历史落库(上下文由 Flowise chatId 记忆机制维持)
- Redis(单实例 Session 存内存;集群化时再引入)

## 4. 技术选型与决策记录

| 决策点 | 结论 | 理由摘要 |
|---|---|---|
| 拆分策略 | 垂直切片优先 | 第一周暴露 Keycloak/Flowise 集成风险,始终可演示 |
| 技术栈 | React + Ant Design / Spring Boot / PostgreSQL | PRD 首选组合,企业客户技术审查友好 |
| 认证架构 | **BFF 模式**(Spring Security OAuth2 Client) | Token 不进浏览器,安全审查加分;前端零认证逻辑;后续可演化为网关 |
| 运行环境 | 本地 docker-compose | 一键启动、可重建,是后续部署蓝本 |
| 运行交互 | Portal 自建 Chat 窗口(SSE 流式) | 流量全经 Portal,权限/审计/Token 统计有抓手 |
| Agent 来源 | Flowise 预置 Chatflow + Portal 登记表单 | Designer 嵌入留给子项目 2,切片保持最窄 |
| LLM | Azure OpenAI(Flowise Azure ChatOpenAI 节点) | 使用方已有 Azure 资源 |

## 5. 系统架构

```
                       ┌─────────────────────────┐
                       │  浏览器 (React + AntD)   │
                       └────────────┬────────────┘
                 HttpOnly Session Cookie / REST + SSE
                                    │
                       ┌────────────▼────────────┐
                       │ Portal Backend           │
                       │ (Spring Boot BFF 单体)   │
                       │ auth / agent / chat      │
                       └──┬─────────┬─────────┬──┘
              OIDC 授权码 │         │ Prediction API │ JPA
                          │         │ (API Key,内网) │
                 ┌────────▼──┐ ┌────▼─────┐ ┌───────▼──────┐
                 │ Keycloak  │ │ Flowise  │ │ PostgreSQL   │
                 │ softmatrix│ │ 预置     │ │ portal /     │
                 │ realm     │ │ Chatflow │ │ keycloak /   │
                 └───────────┘ └────┬─────┘ │ flowise 三库 │
                                    │ HTTPS └──────────────┘
                              ┌─────▼──────┐
                              │ Azure      │
                              │ OpenAI     │
                              └────────────┘
```

**关键边界**:

1. 浏览器只与 Portal Backend 通信,不直连 Flowise;与 Keycloak 的交互仅限登录页重定向
2. Flowise API Key 只存在于 Portal Backend 配置中,Flowise 端口不对宿主机外暴露
3. 一个 PostgreSQL 实例,portal / keycloak / flowise 三个独立数据库

## 6. 数据模型

portal 库,切片仅一张业务表。

**`agent` 表**:

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | UUID | PK | |
| name | varchar(100) | not null | 名称 |
| description | text | | 描述 |
| flowise_chatflow_id | varchar(64) | not null | 关联的 Flowise Chatflow ID |
| owner | varchar(100) | not null | 创建者,取 OIDC preferred_username |
| created_at / updated_at | timestamptz | not null | 时间戳 |

PRD 中 Agent 的分类、标签、状态、发布等字段在子项目 2 加深时扩展。

## 7. API 设计

全部挂在 `/api` 下,由 Session Cookie 保护;未认证一律返回 401(不重定向,重定向由前端发起)。

| 接口 | 说明 |
|---|---|
| `GET /api/me` | 当前用户信息 `{username, name}`,前端判断登录态 |
| `GET /api/agents` | Agent 列表 |
| `POST /api/agents` | 登记 Agent `{name, description, flowiseChatflowId}` |
| `PUT /api/agents/{id}` | 编辑 |
| `DELETE /api/agents/{id}` | 删除 |
| `POST /api/agents/{id}/chat` | 对话,请求体 `{sessionId, message}`,响应 `text/event-stream` |

认证相关路径由 Spring Security 提供:`/oauth2/authorization/keycloak`(发起登录)、`/login/oauth2/code/keycloak`(回调)、`/logout`(登出,同时调 Keycloak end-session 端点)。

**Chat 流式约定**:后端用 WebClient 调 Flowise Prediction API 的 streaming 模式,将 token 事件原样转发为 SSE;`sessionId` 由前端打开 Chat 窗口时生成(UUID),透传为 Flowise `chatId` 以维持多轮上下文,刷新页面即新会话。若流式代理遇到不可克服的问题,降级为同步 JSON 一次性返回,接口路径与请求体不变。

## 8. 关键流程

### 8.1 登录(BFF / OIDC 授权码)

1. 前端启动时调 `GET /api/me`,得 401 → 跳转 `/oauth2/authorization/keycloak`
2. 后端 302 重定向到 Keycloak 登录页
3. 用户认证成功,Keycloak 携授权码回调 `/login/oauth2/code/keycloak`
4. 后端以授权码换 Token(服务端完成,不经浏览器),建立 Session,Set-Cookie(HttpOnly)
5. 重定向回前端首页,后续请求凭 Cookie 通行

### 8.2 Chat 对话

1. 前端 `POST /api/agents/{id}/chat` `{sessionId, message}`
2. 后端查 `agent` 表得 `flowise_chatflow_id`
3. WebClient 调 Flowise `POST /api/v1/prediction/{chatflowId}`(streaming,chatId=sessionId)
4. Flowise 调 Azure OpenAI,token 流经 Flowise → 后端 → 浏览器逐级 SSE 转发,前端渐进渲染

## 9. 错误处理

统一错误响应格式 `{code, message}`,前端 Axios 拦截器统一处理并以 AntD message 提示。

| 场景 | 处理 |
|---|---|
| 未登录 / Session 过期 | 401,前端拦截后跳转登录 |
| 登记时 Chatflow ID 无效 | 后端调 Flowise `GET /api/v1/chatflows/{id}` 校验,不存在返回 400 |
| Flowise 不可达 / 超时(60s) | 502,Chat 该条消息显示"运行失败,请重试",会话可继续 |
| Azure OpenAI 报错(配额/Key) | Flowise 错误信息透传为 502,前端同上 |
| SSE 中途断开 | 前端保留已渲染部分,其后追加错误标记 |
| 表单校验 | 前端 AntD 校验 + 后端 Bean Validation,400 返回字段级错误 |

## 10. 仓库结构

Monorepo:

```
softmatrix/
├── docs/                          # PRD 与设计文档
├── infra/
│   ├── docker-compose.yml         # Keycloak + Flowise + PostgreSQL
│   ├── keycloak/realm-export.json # softmatrix realm + 测试用户
│   └── postgres/init.sql          # 创建三个数据库
├── backend/                       # Spring Boot (Maven)
│   └── …/portal/
│       ├── auth/                  # SecurityConfig、/api/me
│       ├── agent/                 # Controller / Service / Repository / Entity
│       ├── chat/                  # FlowiseClient(WebClient)+ SSE 转发
│       └── common/                # 统一异常处理、错误响应
└── frontend/                      # React + Vite + TypeScript + Ant Design
    └── src/
        ├── api/                   # Axios 实例 + 401 拦截 + SSE 客户端
        ├── pages/                 # AgentListPage / ChatPage
        └── layouts/               # 顶栏(用户名、登出)
```

## 11. 测试策略

- **后端**:Service 层单元测试;`FlowiseClient` 用 MockWebServer 集成测试(含流式转发);Controller 层用 `spring-security-test` 模拟登录态验证鉴权(未登录 401)
- **前端**:构建与 TypeScript 类型检查通过,不为切片写组件测试
- **端到端**:手工验收清单(第 12 节),自动化 E2E 留给后续子项目

## 12. 验收标准(切片 Definition of Done)

1. `docker compose up` 一键启动三个依赖服务,realm 与测试用户自动导入
2. 未登录访问门户 → 自动跳转 Keycloak 登录页
3. 测试用户登录后回到门户,顶栏显示用户名;登出后再访问 API 得 401
4. 通过表单登记 Agent(关联预置 Chatflow),列表可见、可编辑、可删除
5. 填写不存在的 Chatflow ID,登记被拒并有明确提示
6. Chat 发送消息,回复流式渲染;同一窗口多轮对话有上下文
7. 停掉 Flowise 容器后发消息,界面显示友好错误而非白屏
8. 后端测试全部通过
