# EAP 子项目三 · 嵌入 Flowise 设计器 设计文档

**日期**:2026-07-13
**状态**:已与产品负责人逐节确认
**上游文档**:[Softmatrix Enterprise Agent Platform PRD V1.0](../../Softmatrix%20Enterprise%20Agent%20Platform%20PRD%20V1.0.md) · [子项目二 · Agent 管理加深设计](2026-07-09-agent-management-design.md)

---

## 1. 背景与范围

子项目二完成了 Agent 元数据、生命周期状态机、筛选与导入导出,但创建/修改流程编排仍要求用户离开门户、直接操作 Flowise。本子项目实现 PRD 模块六的核心:**在门户内嵌入 Flowise 设计器**,用户以 Agent 为中心完成编排,全程不离开门户。

本子项目基于子项目二的成果(`worktree-agent-management` 分支,PR #1)继续开发。

### 1.1 范围内

- Agent 列表新增**"编排"**入口,仅 `DRAFT` / `DISABLED` 状态可见;编辑已发布 Agent 的流程须先撤回
- 门户内新增**设计器页面**(路由 `/agents/{id}/design`),以 iframe 嵌入该 Agent 绑定的 Flowise 画布 `/canvas/{chatflowId}`
- **新建 Agent 自动建流**:Chatflow ID 留空时,后端自动在 Flowise 创建空白 Chatflow 并绑定;表单保留"绑定已有 Chatflow ID"高级选项
- 设计器地址可配置,经新增的 `GET /api/config` 端点下发给前端

### 1.2 范围外(留给后续子项目)

- 流程**版本历史 / 快照**(已确认本期不做;版本治理属后续独立子项目)
- **反向代理网关式透明认证**(见第 3 节方案 A,可与"企业 IAM 对接"子项目合并考虑)
- RBAC、审批流(沿用现状:任何登录用户可管理所有 Agent)
- PRD 模块六中的"运行 Workflow"(门户 Chat 已覆盖)与"导入/导出 JSON"(子项目二已交付)

## 2. 关键决策记录

| 决策点 | 结论 | 理由 |
|---|---|---|
| 入口粒度 | 按 Agent 进入编排,不做独立"流程设计"页面 | 用户心智模型始终是 Agent,Flowise 概念不外露 |
| 嵌入与认证 | **方案 B:直接 iframe + 共享设计账号一次登录** | 实现量最小、无代理难题;产出可完整复用于将来的方案 A(代理网关) |
| 编辑门控 | 仅 `DRAFT` / `DISABLED` 可进设计器 | Flowise 保存即生效,禁止直接改已发布 Agent 的线上行为;复用子项目二状态机的"撤回"语义 |
| 新建方式 | 默认自动建空白流并绑定,保留手工填 ID 高级选项 | 新建体验一步到位,同时兼容登记 Flowise 中已有流 |
| 版本能力 | 本期不做 | 开源版 Flowise 无内置版本历史;版本治理在子项目二设计时已明确列为范围外 |
| 设计器地址 | 新配置项 `flowise.designer-base-url`,经 `/api/config` 下发 | 浏览器可达地址与后端内网调用地址(`flowise.base-url`)在生产环境常常不同 |

## 3. 架构与认证

### 3.1 结构(方案 B)

浏览器同时直连两个源:门户(React + Spring Boot BFF)与 Flowise。设计器页面中的 iframe 直接加载 `{designerBaseUrl}/canvas/{chatflowId}`;iframe 内所有 API 调用由 Flowise 自身的 JWT Cookie 认证,与门户会话互不相干。

```
┌────────────────────────────────────────────┐
│              浏览器                          │
│  ┌──────────────┐   ┌───────────────────┐  │
│  │ Portal 页面   │   │ iframe:Flowise    │  │
│  │ (门户会话)    │   │ 画布 (Flowise JWT) │  │
│  └──────┬───────┘   └─────────┬─────────┘  │
└─────────┼─────────────────────┼────────────┘
     REST/SSE(BFF)        直连 :3000
          │                     │
   ┌──────▼───────┐      ┌──────▼──────┐
   │ Portal 后端   │──────▶│  Flowise    │
   │              │ API Key│             │
   └──────────────┘      └─────────────┘
```

- **首次登录**:用户第一次进设计器时,iframe 内出现 Flowise 登录页,用共享设计账号(现为 `admin@softmatrix.local`)登录一次,此后 JWT Cookie 长期保持;会话过期后 iframe 内自然再现登录页,登录即恢复,门户侧无需处理
- 后端调用 Flowise 仍走既有 API Key 通道,本子项目不改动

### 3.2 可行性实证(2026-07-13,Flowise 3.1.2)

- Flowise 根页面**未设置** `X-Frame-Options` / CSP `frame-ancestors`,iframe 嵌入不被浏览器拦截
- 开发环境 `localhost` 各端口互为 same-site,Flowise Cookie 在 iframe 内正常收发;生产环境将门户与 Flowise 部署在同一站点(同一注册域)的不同子域名下即可保持该性质
- Flowise UI 登录为账号体系(`POST /api/v1/login`),API Key(Bearer)对 `/api/v1/chatflows` CRUD 有效(子项目二已在用)

### 3.3 已接受的 MVP 限制

1. 共享设计账号的密码需告知所有需要编排的用户
2. iframe 内可自由游走 Flowise 全部界面(凭据、设置等)
3. "仅 DRAFT/DISABLED 可编排"的门控在门户 UI 层,无法阻止绕过门户直连 Flowise 的用户

三条均与当前"任何登录用户可管理一切"的整体姿态一致;彻底解决依赖后续的代理网关(方案 A)与 RBAC 子项目。

## 4. 后端设计

无新表、无迁移。

### 4.1 新建 Agent 自动建流(`POST /api/agents` 行为扩展)

- `flowiseChatflowId` 为空 → 调用既有 `FlowiseClient.createChatflow(name, 空白 flowData)`(空白 flowData 为 `{"nodes":[],"edges":[]}`),将返回的新 Chatflow ID 绑定到 Agent
- `flowiseChatflowId` 有值 → 维持现有行为(`chatflowExists` 校验后登记)
- 先建流、后落库,不做分布式事务:建流失败则 502 `FLOWISE_ERROR`、Agent 不落库;落库失败则 Flowise 留下孤儿流(与子项目二导入功能同一姿态)

### 4.2 配置下发端点

`GET /api/config`(需登录)→ `{"designerBaseUrl": "http://localhost:3000"}`

- 值来自新配置项 `flowise.designer-base-url`,默认取 `flowise.base-url` 的值
- 新增极简 `ConfigController`;前端用它拼 iframe 地址

## 5. 前端设计

### 5.1 列表页与新建表单

- `DRAFT` / `DISABLED` 行的操作列新增**"编排"**按钮 → 跳 `/agents/{id}/design`
- 新建表单:Chatflow ID 改为折叠的"高级选项",留空时提示"将自动创建空白流程"

### 5.2 设计器页(沉浸式全屏,布局 A)

- 隐藏门户侧边栏/页头,仅保留一条细顶栏:**← 返回** + Agent 名称 + 状态标签 + "保存即生效"提示
- iframe 占满其余全部视口,`src = {designerBaseUrl}/canvas/{flowiseChatflowId}`
- 进入时按 Agent 状态分流:
  - `DRAFT` / `DISABLED` → 渲染 iframe
  - `PUBLISHED` → 不渲染 iframe,提示"该 Agent 已发布,修改流程请先撤回",并提供撤回按钮(撤回成功后原地进入编排)
  - Agent 不存在 → 404 提示 + 返回列表

## 6. 错误处理与边界

| 场景 | 行为 |
|---|---|
| 自动建流时 Flowise 不可达/报错 | 502 `FLOWISE_ERROR`,Agent 不落库,前端提示"创建流程失败" |
| 建流成功但 Agent 落库失败 | Flowise 留孤儿流,不回滚(明示接受) |
| 直接访问已发布 Agent 的设计器路由 | 拦截页 + 撤回入口(见 5.2) |
| iframe 内 Flowise 会话过期 | iframe 内出现 Flowise 登录页,重新登录即恢复 |
| Flowise 服务不可达 | iframe 显示加载失败;MVP 不做健康探测 |

## 7. 测试策略

- **后端单测**
  - `AgentServiceTest`:create 留空 chatflowId → 调用 `createChatflow` 并绑定返回 ID;建流失败 → 502 且不落库;带 chatflowId → 原校验路径回归
  - `ConfigControllerTest`(新):未登录 401;登录后返回配置的 `designerBaseUrl`
- **前端**:`tsc && vite build` 通过;不新增测试框架
- **人工 E2E**(真实 docker-compose 环境):新建 Agent(留空自动建流)→ 进设计器 → iframe 内首次登录 → 编辑画布并保存 → 返回、发布 → Chat 验证流程变更生效;另验证 PUBLISHED 拦截页与"绑定已有 Chatflow ID"高级选项
