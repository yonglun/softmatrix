# EAP 子项目二 · Agent 管理加深 设计文档

**日期**:2026-07-09
**状态**:已与产品负责人逐节确认
**上游文档**:[Softmatrix Enterprise Agent Platform PRD V1.0](../../Softmatrix%20Enterprise%20Agent%20Platform%20PRD%20V1.0.md) · [子项目一 · 垂直切片设计](2026-07-07-vertical-slice-design.md)

---

## 1. 背景与范围

子项目一(垂直切片)打通了 Keycloak 登录 → 门户看到 Agent → Chat 运行的最窄链路,`agent` 表只有最小字段(name、description、flowise_chatflow_id、owner、时间戳)。

路线图中的"子项目二"原本捆绑了两块能力:**Agent 管理加深**(PRD 模块五)与**嵌入 Flowise 设计器**(PRD 模块六)。经讨论决定拆分:**本子项目只做 Agent 管理加深**,嵌入设计器移到后续独立子项目。

### 1.1 范围内

- Agent 元数据加深:**分类**(category,单选自由文本)、**标签**(tags,多选自由文本)
- Agent **生命周期状态机**:草稿 / 已发布 / 已停用,并门控 Chat 运行
- **发布 / 停用 / 撤回 / 重新启用**的状态转换端点
- Agent 列表**筛选**:按分类、标签、状态、名称关键字(服务端查询)
- Agent **导入 / 导出**:打包 = Portal 元数据 + Flowise 流定义
- 引入 **Flyway** 做受控 schema 迁移,存量 Agent 迁移为已发布

### 1.2 范围外(留给后续子项目)

- 嵌入 Flowise 设计器(模块六)
- 组织管理、角色权限(RBAC)—— 本子项目沿用切片一:任何登录用户可管理所有 Agent
- Agent 复制、版本治理、审批
- Workspace、Runtime 监控、Dashboard

## 2. 关键决策记录

| 决策点 | 结论 | 理由 |
|---|---|---|
| 本子项目范围 | 只做 Agent 管理加深,不含嵌入设计器 | 拆成两块,降风险、快交付;设计器集成风险高,单列 |
| 状态机 | 三态:DRAFT → PUBLISHED → DISABLED | 覆盖"草稿未发布/已发布可运行/停用保留"三种真实语义 |
| 状态门控 | 仅 PUBLISHED 可被 Chat 运行 | 状态直接门控运行入口,语义清晰 |
| 状态修改方式 | 专用转换端点,普通 PUT 不能改 status | 防止普通编辑绕过状态机 |
| 导入导出内容 | 元数据 + Flowise 流定义 | 真正可移植,跨环境可用 |
| 导入行为 | 总在 Flowise 新建流,落为 DRAFT | 跨环境可用;不与原 Agent 共享流;需人工检查后发布 |
| 分类 / 标签 | 分类单选自由文本;标签多选自由文本 | 无需受控字典表,数组列足够筛选(方案 A) |
| 数据模型 | 就地扩展 agent 表(方案 A) | 与切片一单模块结构一致,改动最小、可测 |
| 迁移 | 引入 Flyway,ddl-auto 改为 validate | 可重放、存量数据不被破坏,为后续建表打基础 |
| 临时权限 | 任何登录用户可管理所有 Agent | RBAC 是后续子项目;owner 仍记录创建者 |

## 3. 数据模型与迁移

### 3.1 `agent` 表新增字段

在切片一字段(id, name, description, flowise_chatflow_id, owner, created_at, updated_at)基础上新增:

| 字段 | 类型 | 约束 / 默认 | 说明 |
|---|---|---|---|
| `category` | varchar(50) | 可空 | 单选自由文本分类 |
| `tags` | text[] | not null,默认 `'{}'` | 自由标签数组 |
| `status` | varchar(16) | not null,默认 `DRAFT` | `DRAFT` / `PUBLISHED` / `DISABLED` |
| `published_at` | timestamptz | 可空 | 最近一次进入 PUBLISHED 的时间 |

### 3.2 Flyway 迁移

引入 Flyway,`ddl-auto` 由 `update` 改为 `validate`(schema 只由迁移脚本演进)。

- `V1__baseline.sql`:建立当前(切片一)`agent` 表结构,与现有实体一致
- `V2__agent_management.sql`:为 `agent` 增加上述 4 列(`status` 列默认 `DRAFT`);随后在同一脚本中将**所有存量行一次性置为 `PUBLISHED`**(`UPDATE agent SET status='PUBLISHED', published_at=now();`),避免破坏切片一已能运行的演示 Agent。此后新建的 Agent 才走实体默认值 `DRAFT`

> 注意:`text[]` 为 PostgreSQL 特有类型,迁移脚本面向 PostgreSQL 编写(见第 7 节测试策略对 H2/Testcontainers 的处理)。

## 4. 状态机与门控

```
新建 ──▶ DRAFT ──发布──▶ PUBLISHED ──停用──▶ DISABLED
              ◀──撤回──         ◀─重新启用──
```

- **合法转换**:DRAFT→PUBLISHED(发布)、PUBLISHED→DISABLED(停用)、DISABLED→PUBLISHED(重新启用)、PUBLISHED→DRAFT(撤回)
- **非法转换**:返回 `400 INVALID_TRANSITION`
- **运行门控**:`POST /api/agents/{id}/chat` 前置检查,状态非 `PUBLISHED` 返回 `409 AGENT_NOT_RUNNABLE`,前端提示"该 Agent 未发布,无法运行"
- **发布副作用**:进入 PUBLISHED 时写 `published_at`
- **编辑与状态分离**:`PUT /api/agents/{id}` 只改 name/description/category/tags,**不接受 status**;状态只能经转换端点修改

## 5. API 设计

沿用切片一的 agent API(全部 `/api` 下,由 Session Cookie 保护),扩展如下。

| 接口 | 说明 |
|---|---|
| `GET /api/agents?category=&tag=&status=&keyword=` | 列表,支持按分类、单个标签、状态、名称关键字筛选(服务端查询,参数均可选) |
| `GET /api/agents/categories` | 返回已有分类去重列表(供表单下拉) |
| `GET /api/agents/tags` | 返回已有标签去重列表(供表单/筛选下拉) |
| `POST /api/agents` | 请求体含 name、description、flowiseChatflowId、category、tags(**不含 status**);创建为 DRAFT |
| `PUT /api/agents/{id}` | 请求体含 name、description、category、tags(**不含 status、不改 flowiseChatflowId**) |
| `DELETE /api/agents/{id}` | 删除(任意状态) |
| `POST /api/agents/{id}/publish` | DRAFT/DISABLED → PUBLISHED,写 published_at |
| `POST /api/agents/{id}/disable` | PUBLISHED → DISABLED |
| `POST /api/agents/{id}/withdraw` | PUBLISHED → DRAFT |
| `GET /api/agents/{id}/export` | 返回打包 JSON(元数据 + Flowise 流定义),`Content-Disposition: attachment` |
| `POST /api/agents/import` | 上传打包 JSON,在 Flowise 新建 Chatflow 后登记为新 Agent(DRAFT) |
| `POST /api/agents/{id}/chat` | (切片一)路径不变,内部新增"状态须为 PUBLISHED"前置校验 |

**转换端点响应**:统一返回更新后的 `AgentResponse`。

**`AgentResponse` 扩展**:在切片一字段基础上增加 `category`、`tags`、`status`、`publishedAt`。

**`AgentRequest` 扩展**:增加 `category`(可空)、`tags`(可空,默认空数组);仍含 `name`、`description`、`flowiseChatflowId`;不含 `status`。创建与更新复用同一 DTO;**更新时忽略 `flowiseChatflowId`**——Agent 与 Chatflow 的绑定在创建时确定,之后不可改(需换流请重新导入或新建 Agent)。

## 6. 导入 / 导出

### 6.1 打包文件结构

```json
{
  "softmatrixVersion": "1",
  "agent": { "name": "...", "description": "...", "category": "...", "tags": ["..."] },
  "flow":  { "name": "...", "flowData": { /* Flowise 节点图 JSON */ } }
}
```

### 6.2 导出流程(`GET /api/agents/{id}/export`)

1. 查 `agent` 元数据
2. 调 Flowise `GET /api/v1/chatflows/{cfId}` 取流定义(flowData)
3. 组装打包 JSON,以 `Content-Disposition: attachment; filename="softmatrix-agent-{name}.json"` 返回

### 6.3 导入流程(`POST /api/agents/import`)

1. 接收上传 JSON,校验结构:`agent.name` 与 `flow.flowData` 必需,否则 `400`
2. 调 Flowise `POST /api/v1/chatflows` 用 `flow.name` + `flow.flowData` 新建流,得**新 cfId**
3. 存 `agent` 行:元数据取自 `agent.*`,`flowise_chatflow_id` = 新 cfId,`owner` = 当前用户,`status` = DRAFT
4. 返回新 `AgentResponse`

### 6.4 FlowiseClient 扩展

- `String getChatflow(String id)` —— 返回 Chatflow 完整 JSON(含 flowData),供导出
- `String createChatflow(String name, JsonNode flowData)` —— 新建流,返回新 cfId,供导入
- 失败(Flowise 不可达 / 4xx)映射为 `502 FLOWISE_ERROR`

## 7. 前端

- **AgentListPage**:
  - 顶部筛选工具栏:名称关键字、分类下拉、标签下拉、状态下拉(值取自 `/categories`、`/tags`)
  - 新增"状态"列,徽标区分草稿 / 已发布 / 已停用
  - 操作列按状态动态渲染:草稿显 编辑/发布/导出/删除;已发布显 运行/编辑/停用/撤回/导出/删除;已停用显 编辑/重新启用/导出/删除。仅已发布可点"运行"
  - 工具栏"导入"按钮:选 `.json` → 调 import → 刷新列表
  - 每行"导出":触发浏览器下载打包 JSON
- **新建/编辑弹窗**:增加 分类(AutoComplete,可选已有或新输)、标签(Select `mode="tags"`)
- **ChatPage**:运行返回 `409` 时提示"该 Agent 未发布,无法运行"
- `api/types.ts`:`Agent`/`AgentRequest` 增加 category、tags、status、publishedAt
- `api/agents.ts`:新增 publish/disable/withdraw/export/import、`listCategories`、`listTags`,并给 `listAgents` 增加筛选参数

## 8. 错误处理

沿用切片一统一错误格式 `{code, message}`。

| 场景 | 处理 |
|---|---|
| 运行未发布的 Agent | `409 AGENT_NOT_RUNNABLE`,前端提示未发布 |
| 非法状态转换 | `400 INVALID_TRANSITION` |
| 导入结构非法(缺 name/flowData) | `400 IMPORT_INVALID` |
| 导入时 Flowise 建流失败 / 不可达 | `502 FLOWISE_ERROR` |
| 导出时 Flowise 取流失败 / 不可达 | `502 FLOWISE_ERROR` |
| 表单校验(名称必填、长度) | 前端 AntD + 后端 Bean Validation,`400` |

## 9. 测试策略

- **纯逻辑单测(无库、无网络)**:`AgentService` 状态机转换(合法/非法)、导入导出的打包/解包与结构校验,用 Mockito 打桩仓库与 FlowiseClient
- **FlowiseClient**:MockWebServer 覆盖 `getChatflow`、`createChatflow`(含错误映射为 502)
- **Controller**:`spring-security-test` 覆盖新端点鉴权(未登录 401)、状态门控(未发布运行 409)
- **仓库层 / 迁移**:因 `text[]` 为 PostgreSQL 特有类型,H2 不兼容;仓库与 Flyway 迁移测试改用 **Testcontainers(真 PostgreSQL)**,验证迁移可跑通、筛选查询正确、存量数据迁移为 PUBLISHED。纯逻辑单测仍用 mock,不依赖容器
- **前端**:类型检查 + 生产构建通过
- **验收**:见第 10 节

## 10. 验收标准(Definition of Done)

1. Flyway 迁移在空库与切片一存量库上都能跑通;存量 Agent 迁移后为 `PUBLISHED` 且可运行
2. 新建 Agent 默认 DRAFT,不可运行;发布后可运行
3. 停用后运行被拒(409,前端提示未发布);重新启用后恢复可运行;撤回回到草稿
4. 非法状态转换返回 400
5. 列表可按分类、标签、状态、名称关键字筛选;分类/标签下拉取自实际数据
6. 新建/编辑可设置分类与多个标签
7. 导出一个 Agent 得到含元数据 + flowData 的 JSON;将其导入得到一个新的 DRAFT Agent(Flowise 中新建了流)
8. 导入结构非法被拒(400);Flowise 不可达时导入/导出返回 502
9. 后端测试(单测 + MockWebServer + Testcontainers 迁移/仓库测试)全部通过;前端类型检查 + 构建通过
