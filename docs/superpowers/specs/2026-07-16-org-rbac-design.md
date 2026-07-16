# EAP 子项目四 · 组织管理与角色权限(RBAC) 设计文档

**日期**:2026-07-16
**状态**:已与产品负责人逐节确认
**上游文档**:[Softmatrix Enterprise Agent Platform PRD V1.0](../../Softmatrix%20Enterprise%20Agent%20Platform%20PRD%20V1.0.md) · [子项目三 · 嵌入 Flowise 设计器设计](2026-07-13-flowise-designer-embed-design.md)

---

## 1. 背景与范围

子项目一~三交付了登录、Agent 管理、Chat 运行与设计器嵌入,但一直沿用"任何登录用户可管理一切"的临时姿态;用户只存在于 Keycloak,门户没有用户表,也没有任何角色概念。本子项目实现 PRD 模块二(组织管理)与模块三(角色权限),对应验收标准"支持用户、部门、角色、组织管理,并与 Keycloak 同步"与"Portal 实现统一 RBAC"。

本子项目基于子项目三成果(PR #2 已合并至 master)继续开发。

### 1.1 范围内

- 组织管理:单组织 + 任意深度部门树(含负责人、移动)、岗位字典、用户管理(创建/编辑/停用/重置密码/部门岗位归属)
- 用户写通 Keycloak:门户是用户管理唯一入口,经 Keycloak Admin API 写入
- RBAC:内置四角色 + 自定义角色;用户可多角色,权限取并集;全局操作级权限(9 项权限目录)
- 权限执行:后端 `@PreAuthorize` 声明式拦截(安全边界),前端菜单/按钮/路由三层显隐(体验)
- 既有 Agent/Chat 端点全部纳入权限管控

### 1.2 范围外(留给后续子项目)

- Workspace 与 Agent 实例级隔离("哪些人能碰哪个 Agent")—— Workspace 子项目
- 管理操作审计日志(Auditor 本期为全平台只读)—— 与 Runtime 子项目一并设计
- Workspace Admin 角色 —— 随 Workspace 子项目引入
- 企业 IAM 联邦(Keycloak Broker)、Keycloak 组映射
- 用户硬删除(以"停用"语义交付 PRD 模块二的"删除",理由见 §4.3)

## 2. 关键决策记录

| 决策点 | 结论 | 理由 |
|---|---|---|
| 用户主权 | **门户主导,写通 Keycloak** | 统一门户愿景;满足 DoD"与 Keycloak 同步";管理员不必往返两个系统 |
| 角色存储 | 门户 PostgreSQL,不进 Keycloak/Token | PRD"Portal 做权限";改角色立即生效,无需等 Token 刷新;认证与授权边界干净 |
| 角色集合 | 内置四角色(Platform Admin / Agent Developer / Business User / Auditor)+ 自定义角色 | PRD §3 的四类角色;Workspace Admin 留待 Workspace 子项目;自定义角色满足企业差异化 |
| 角色基数 | 用户可持多角色,权限取并集 | 兼职场景;并集语义简单可预测 |
| 组织模型 | 单组织(种子根节点"总部")+ 部门树 + 岗位字典 | 覆盖 PRD 示例;多组织即多租户,PRD §8 明确排除 |
| 权限粒度 | 全局操作级(9 项),不做实例级 ACL | 与 PRD 模块三"菜单/Agent/运行"三类权限吻合;实例级隔离是 Workspace 的职责 |
| Keycloak 客户端 | 手写薄 WebClient(`KeycloakAdminClient`),不用官方 admin-client 库 | 与 `FlowiseClient` 模式同构;只用四五个端点,官方库依赖重且与服务端版本强耦合 |
| 权限判断 | `@PreAuthorize("@perm.has('…')")`,每请求读库 + request-scoped 记忆化 | 即时生效;1~2 次索引查询在本期规模无感;将来有压力再加 TTL 缓存 |
| 审计日志 | 本期不做 | 会话/日志本就是 Runtime 子项目职责,届时一并设计 |

## 3. 数据模型

新增 Flyway 迁移 `V3__org_rbac.sql`,五张表:

| 表 | 关键字段 | 说明 |
|---|---|---|
| `department` | id, name, parent_id(自引用,可空), manager_user_id(可空) | 部门树;迁移种子根节点"总部"(不可删);新建部门必须有父节点——单组织的落地形态 |
| `position` | id, name(唯一) | 岗位字典,纯下拉数据源 |
| `app_user` | id, keycloak_id(唯一), username(唯一), name, email, enabled, department_id(可空), position_id(可空) | Keycloak 用户的门户镜像 + 组织归属 |
| `role` | id, name(唯一), description, built_in | 内置角色 built_in=true,不可编辑不可删除 |
| `role_permission` / `user_role` | 纯关联表 | 角色↔权限、用户↔角色(多对多) |

### 3.1 权限目录

Java 枚举(不建表),9 项,对应 PRD 模块三的三类权限:

- **Agent 域**:`AGENT_VIEW`(列表/详情 + 菜单可见)、`AGENT_MANAGE`(增删改/导入导出)、`AGENT_PUBLISH`(发布/停用/撤回)、`AGENT_DESIGN`(编排)、`AGENT_RUN`(Chat 运行)
- **组织域**:`ORG_VIEW`、`ORG_MANAGE`(部门/岗位/用户的增改停删)
- **角色域**:`ROLE_VIEW`、`ROLE_MANAGE`(角色 CRUD + 用户角色分配)

### 3.2 内置角色矩阵(迁移种子)

| 权限 | Platform Admin | Agent Developer | Business User | Auditor |
|---|---|---|---|---|
| AGENT_VIEW | ✓ | ✓ | ✓ | ✓ |
| AGENT_RUN | ✓ | ✓ | ✓ | ✗ |
| AGENT_MANAGE / PUBLISH / DESIGN | ✓ | ✓ | ✗ | ✗ |
| ORG_VIEW | ✓ | ✗ | ✗ | ✓ |
| ORG_MANAGE | ✓ | ✗ | ✗ | ✗ |
| ROLE_VIEW | ✓ | ✗ | ✗ | ✓ |
| ROLE_MANAGE | ✓ | ✗ | ✗ | ✗ |

Agent Developer 含 `AGENT_PUBLISH`:MVP 无审批流(PRD §8 排除"Agent 审批"),开发者自行发布。

### 3.3 用户引导(bootstrap)

- **JIT 镜像**:登录成功时按 ID Token 把用户 upsert 进 `app_user`(sub→keycloak_id、username、name、email),已有行则刷新资料
- **首任管理员**:配置项 `portal.bootstrap-admin-username=admin`,该用户 JIT 落库时若尚无角色则自动获得 Platform Admin
- **默认无角色**:其余 JIT 用户不自动授角色(最小权限),前端展示"暂无权限,请联系管理员"整页提示;现有种子用户 `user` 需管理员分配一次(演示环境两次点击)

## 4. Keycloak 集成

### 4.1 服务账号

`realm-export.json` 新增 confidential client **`portal-admin`**(service account,授 realm-management 的 `manage-users` + `view-users`),secret 走环境变量 `KEYCLOAK_ADMIN_CLIENT_SECRET`。realm 导入只在全新库生效,**已有开发环境需手工建一次该 client**(实现计划将写明步骤)。

### 4.2 KeycloakAdminClient(薄 WebClient 封装)

1. `token()` — client_credentials 换 admin token,内存缓存至过期前
2. `createUser(username, name, email, 初始密码)` — 密码标记 `temporary=true`,用户首次登录被 Keycloak 强制改密
3. `updateUser(keycloakId, name, email)`
4. `setEnabled(keycloakId, bool)`
5. `resetPassword(keycloakId, 新密码)` — 同样 temporary

### 4.3 写入次序与失败语义

与子项目二/三对 Flowise 的姿态一致,不做分布式事务:

- **先 Keycloak、后门户库**。KC 失败 → `502 KEYCLOAK_ERROR`,门户库不写;KC 成功后落库失败 → Keycloak 留孤儿用户(该用户登录后 JIT 镜像自愈,实际危害趋近于零)
- 用户名冲突:不预查,直接创建,将 KC 的 409 映射为 `409 USERNAME_TAKEN`
- **不做硬删除**:用户可能是历史 Agent 的 owner(`agent.owner` 为 username 字符串),删除破坏追溯;"离职"以停用交付(`enabled=false`,写通 KC,即刻无法登录)。这是对 PRD 模块二"删除"的明确偏离,已确认接受
- **自我保护**:不能停用自己、不能移除自己的 Platform Admin 角色(`409 SELF_LOCKOUT`)

## 5. 权限执行

### 5.1 后端(安全边界)

- `PermissionChecker` Bean(注册名 `perm`):从 SecurityContext 取登录名 → 查库取该用户全部角色的权限并集 → 判断;request-scoped 记忆化,同一请求只查一次库
- 控制器方法声明 `@PreAuthorize("@perm.has('AGENT_MANAGE')")`
- 未登录 → 401(现状);已登录无权限 → `403 PERMISSION_DENIED`(挂进 `GlobalExceptionHandler`)
- **既有端点补注解**:agents 增删改/导入导出 → `AGENT_MANAGE`;发布/停用/撤回 → `AGENT_PUBLISH`;列表/单查/categories/tags → `AGENT_VIEW`;chat → `AGENT_RUN`;`/api/config`、`/api/me` 仅需登录

### 5.2 前端(体验层)

- `UserInfo` 扩展为 `{ username, name, permissions: string[] }`,启动时 `fetchMe` 一并取回,放 React Context
- **菜单层**:AppLayout 按权限渲染导航(Agent ← AGENT_VIEW;组织与用户 ← ORG_VIEW;角色 ← ROLE_VIEW)
- **按钮层**:AgentListPage `actionsFor` 按权限过滤(编辑/删除/导入导出 ← AGENT_MANAGE;发布/停用/撤回 ← AGENT_PUBLISH;编排 ← AGENT_DESIGN;运行 ← AGENT_RUN);新建/导入按钮同理
- **路由层**:`RequirePermission` 包装组件,直达 URL 无权限渲染 403 Result;新管理页、既有设计器路由(← AGENT_DESIGN)与 Chat 路由(← AGENT_RUN)均纳入守卫;权限为空整页提示"暂无权限"
- 前端显隐只是体验,后端注解才是安全边界

## 6. API 设计

全部挂 `@perm` 注解:读 → `*_VIEW`,写 → `*_MANAGE`;唯一例外是 `PUT /api/users/{id}/roles`——按 §3.1 的目录划分,用户角色分配属 `ROLE_MANAGE`(其余 `/api/users` 写操作属 `ORG_MANAGE`)。

| 端点 | 说明 |
|---|---|
| `GET /api/departments` | 整棵树(全量,规模小) |
| `POST /api/departments`、`PUT /{id}`、`DELETE /{id}` | 增改删;**移动 = PUT 换 parentId**;改名/换负责人同走 PUT;删除要求无子部门无用户(`409 DEPT_NOT_EMPTY`) |
| `GET/POST/PUT/DELETE /api/positions` | 岗位字典 CRUD |
| `GET /api/users?dept=&keyword=&enabled=` | 列表(含部门/岗位/角色摘要) |
| `POST /api/users` | 创建(写通 KC,§4.3) |
| `PUT /api/users/{id}` | 姓名/邮箱(写通)+ 部门/岗位(本地) |
| `PUT /api/users/{id}/enabled` | 停用/启用(写通) |
| `PUT /api/users/{id}/password` | 重置密码(写通,temporary) |
| `PUT /api/users/{id}/roles` | 整体设置角色列表 |
| `GET /api/roles` | 角色列表(含权限集合与用户数) |
| `POST/PUT/DELETE /api/roles/{id}` | 自定义角色 CRUD;built_in → `409 ROLE_BUILT_IN`;有人在用 → `409 ROLE_IN_USE` |
| `GET /api/permissions` | 权限目录(code + 中文名 + 分组),角色编辑页数据源 |

## 7. 前端页面

两个新页面,均在 AppLayout 内:

- **组织与用户页**(`/admin/org`):左侧部门树(AntD Tree;节点操作:新增子部门/重命名/移动/删除/设负责人),右侧选中部门的用户表(含"全部用户"视图);工具栏:新建用户、关键字搜索、岗位字典管理(Modal)。用户行操作:编辑、分配角色、重置密码、停用/启用
- **角色页**(`/admin/roles`):角色列表(名称/描述/权限数/用户数/内置标记);新建/编辑自定义角色 Modal 按"Agent / 组织 / 角色"三组勾选权限;内置角色只读展示矩阵

## 8. 错误处理汇总

沿用现有 `ApiException` / `ErrorResponse` 体系:

| 错误码 | HTTP | 场景 |
|---|---|---|
| `KEYCLOAK_ERROR` | 502 | Admin API 调用失败 |
| `USERNAME_TAKEN` | 409 | 用户名已存在(KC 409 映射) |
| `PERMISSION_DENIED` | 403 | 已登录但无所需权限 |
| `DEPT_NOT_EMPTY` | 409 | 删除仍有子部门/用户的部门 |
| `ROLE_BUILT_IN` | 409 | 修改/删除内置角色 |
| `ROLE_IN_USE` | 409 | 删除仍被分配的角色 |
| `SELF_LOCKOUT` | 409 | 停用自己 / 摘除自己的 Platform Admin |

## 9. 测试策略

- **Service 单测**:mock KeycloakAdminClient + repo;覆盖写通次序、KC 失败不落库、SELF_LOCKOUT、内置角色保护、部门删除守卫
- **KeycloakAdminClient 单测**:MockWebServer(仿 `FlowiseClientTest`),覆盖 token 缓存、409 映射、错误传播
- **PermissionChecker 单测**:多角色并集、无角色空集
- **Controller 测试**:每个受控端点至少一条 403 路径 + 一条有权限路径
- **Repository 测试**:沿用 Docker Postgres
- **人工 E2E 清单**:建部门树 → 建用户(首登强制改密)→ 四内置角色逐一登录验证菜单/按钮显隐与后端 403 → 自定义角色 → 移动部门 → 停用用户即刻踢出 → 锁死防护
