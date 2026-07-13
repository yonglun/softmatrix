# 嵌入 Flowise 设计器(子项目三)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在门户内以 iframe 嵌入 Flowise 画布,用户以 Agent 为中心完成流程编排(方案 B:直接 iframe + 共享设计账号),新建 Agent 支持自动创建空白流。

**Architecture:** 浏览器同时直连门户(BFF)与 Flowise 两个源;iframe 加载 `{designerBaseUrl}/canvas/{chatflowId}`,由 Flowise 自身 JWT Cookie 认证。后端仅两处小改:create 留空 chatflowId 时经既有 `FlowiseClient.createChatflow` 自动建空白流;新增 `GET /api/config` 与 `GET /api/agents/{id}`。前端新增沉浸式 DesignerPage(隐藏门户框架)与列表"编排"入口。

**Tech Stack:** Spring Boot 3.3(Java 17)、Spring Security OAuth2 Client(BFF)、React 18 + AntD 5 + Vite、Flowise 3.1.2(docker-compose)

**设计文档:** `docs/superpowers/specs/2026-07-13-flowise-designer-embed-design.md`

**基线:** master(子项目二已经 PR #1 合并)。无新表、无 Flyway 迁移。

---

## 文件结构总览

| 文件 | 动作 | 职责 |
|---|---|---|
| `backend/src/main/java/com/softmatrix/portal/agent/AgentService.java` | 修改 | create 留空 chatflowId → 自动建空白流 |
| `backend/src/main/java/com/softmatrix/portal/agent/AgentController.java` | 修改 | 新增 `GET /api/agents/{id}` |
| `backend/src/main/java/com/softmatrix/portal/config/ConfigController.java` | 新建 | `GET /api/config` 下发 designerBaseUrl |
| `backend/src/main/resources/application.yml` | 修改 | 新增 `flowise.designer-base-url` |
| `backend/src/test/java/com/softmatrix/portal/agent/AgentServiceTest.java` | 修改 | 自动建流用例(替换"留空必须拒绝"旧用例) |
| `backend/src/test/java/com/softmatrix/portal/agent/AgentControllerTest.java` | 修改 | `GET /{id}` 用例 |
| `backend/src/test/java/com/softmatrix/portal/config/ConfigControllerTest.java` | 新建 | 配置端点用例 |
| `frontend/src/api/types.ts` | 修改 | `AppConfig` 类型 |
| `frontend/src/api/config.ts` | 新建 | `getConfig()` |
| `frontend/src/api/agents.ts` | 修改 | `getAgent(id)` |
| `frontend/src/pages/agentStatus.ts` | 新建 | `STATUS_LABEL` 共享(从 AgentListPage 抽出) |
| `frontend/src/pages/DesignerPage.tsx` | 新建 | 沉浸式设计器页 |
| `frontend/src/pages/AgentListPage.tsx` | 修改 | "编排"按钮;新建表单高级选项 |
| `frontend/src/App.tsx` | 修改 | 设计器路由不套 AppLayout |

---

### Task DE-T1: 后端 · create 留空 chatflowId 时自动建空白流

行为变更:原来 create 留空 chatflowId 返回 400 `INVALID_CHATFLOW`;现在改为调用 `FlowiseClient.createChatflow(name, 空白flowData)` 自动建流并绑定。空白 flowData 为字符串 `{"nodes":[],"edges":[]}`(Flowise 的 flowData 字段是 JSON 字符串,与导入功能同一形态,用 `TextNode` 承载)。

**Files:**
- Modify: `backend/src/main/java/com/softmatrix/portal/agent/AgentService.java`(create 方法,约 40-55 行)
- Test: `backend/src/test/java/com/softmatrix/portal/agent/AgentServiceTest.java`

- [ ] **Step 1: 改写测试——删除旧用例,新增两个自动建流用例**

在 `AgentServiceTest.java` 中**删除** `create_rejects_blank_chatflow` 测试(其断言的旧行为已被规格废除),在原位置**新增**以下两个测试。文件顶部补充 import:`import org.springframework.http.HttpStatus;`(`eq`/`any`/`verify` 已随 `org.mockito.Mockito.*` 静态导入)。

```java
    @Test
    void create_without_chatflow_auto_creates_blank_flow() {
        when(flowise.createChatflow(eq("A"), any())).thenReturn("new-cf");
        AgentRequest req = new AgentRequest("A", "d", null, null, null);

        AgentResponse res = service.create(req, "admin");

        assertThat(res.flowiseChatflowId()).isEqualTo("new-cf");
        assertThat(res.status()).isEqualTo(AgentStatus.DRAFT);
        verify(validator, never()).chatflowExists(any());
    }

    @Test
    void create_without_chatflow_propagates_flowise_error() {
        when(flowise.createChatflow(any(), any())).thenThrow(new ApiException(
                HttpStatus.BAD_GATEWAY, "FLOWISE_ERROR", "在 Flowise 新建流失败"));
        AgentRequest req = new AgentRequest("A", null, null, null, "");

        assertThatThrownBy(() -> service.create(req, "admin"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "FLOWISE_ERROR");
        verify(repo, never()).save(any());
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q test -Dtest=AgentServiceTest`
Expected: FAIL —— `create_without_chatflow_auto_creates_blank_flow` 抛出 `INVALID_CHATFLOW`(旧行为仍在)。

- [ ] **Step 3: 修改 AgentService.create**

将 `AgentService.java` 的 create 方法(现第 40-55 行)替换为:

```java
    /** 留空 chatflowId 时自动在 Flowise 创建空白流并绑定;有值时校验后登记。 */
    public AgentResponse create(AgentRequest req, String owner) {
        String chatflowId;
        if (req.flowiseChatflowId() == null || req.flowiseChatflowId().isBlank()) {
            chatflowId = flowise.createChatflow(req.name(), BLANK_FLOW_DATA);
        } else {
            requireChatflow(req.flowiseChatflowId());
            chatflowId = req.flowiseChatflowId();
        }
        AgentEntity e = new AgentEntity();
        e.setName(req.name());
        e.setDescription(req.description());
        e.setCategory(req.category());
        e.setTags(toArray(req.tags()));
        e.setFlowiseChatflowId(chatflowId);
        e.setOwner(owner);
        e.setStatus(AgentStatus.DRAFT);
        return AgentResponse.from(repo.save(e));
    }
```

并在字段区(`private final com.softmatrix.portal.chat.FlowiseClient flowise;` 之后)新增常量:

```java
    /** Flowise 的 flowData 是 JSON 字符串;空白画布形态与导入功能一致。 */
    private static final com.fasterxml.jackson.databind.JsonNode BLANK_FLOW_DATA =
            com.fasterxml.jackson.databind.node.TextNode.valueOf("{\"nodes\":[],\"edges\":[]}");
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=AgentServiceTest`
Expected: PASS(9 个用例:原 8 个减 1 旧用例加 2 新用例)。

- [ ] **Step 5: 全量后端测试防回归**

Run: `cd backend && mvn -q test`
Expected: PASS(`AgentImportExportTest`、`AgentControllerTest` 等均不受影响;`AgentRepositoryTest` 需 Docker 运行中)。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/agent/AgentService.java \
        backend/src/test/java/com/softmatrix/portal/agent/AgentServiceTest.java
git commit -m "backend: auto-create blank flowise chatflow when creating agent without id"
```

---

### Task DE-T2: 后端 · `GET /api/agents/{id}` 单查端点

设计器页需要按 id 取单个 Agent(状态、名称、chatflowId)。Service 已有 `find(UUID)`(返回实体),补一个返回 DTO 的 `get(UUID)` 与控制器端点。

**Files:**
- Modify: `backend/src/main/java/com/softmatrix/portal/agent/AgentService.java`(`find` 方法附近,约 86 行)
- Modify: `backend/src/main/java/com/softmatrix/portal/agent/AgentController.java`(`tags()` 之后插入)
- Test: `backend/src/test/java/com/softmatrix/portal/agent/AgentServiceTest.java`、`backend/src/test/java/com/softmatrix/portal/agent/AgentControllerTest.java`

- [ ] **Step 1: 写失败的 Service 测试**

在 `AgentServiceTest.java` 追加(文件顶部补 `import java.util.Optional;`):

```java
    @Test
    void get_returns_agent_response() {
        AgentEntity e = stored(AgentStatus.DRAFT);
        assertThat(service.get(e.getId()).id()).isEqualTo(e.getId());
    }

    @Test
    void get_unknown_id_is_404() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "AGENT_NOT_FOUND");
    }
```

- [ ] **Step 2: 跑测试确认编译失败(get 方法不存在)**

Run: `cd backend && mvn -q test -Dtest=AgentServiceTest`
Expected: COMPILE FAIL —— `cannot find symbol: method get(java.util.UUID)`。

- [ ] **Step 3: 实现 Service.get 与控制器端点**

`AgentService.java` 中 `find` 方法之前新增:

```java
    public AgentResponse get(UUID id) { return AgentResponse.from(find(id)); }
```

`AgentController.java` 中 `tags()` 之后新增(字面量路由 `/categories`、`/tags` 优先于路径变量,无冲突):

```java
    @GetMapping("/{id}")
    public AgentResponse get(@PathVariable UUID id) { return service.get(id); }
```

- [ ] **Step 4: 补控制器测试**

在 `AgentControllerTest.java` 追加:

```java
    @Test
    void get_by_id_endpoint() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenReturn(sample(AgentStatus.DRAFT));
        mvc.perform(get("/api/agents/{id}", id).with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.name").value("A"));
    }
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest='AgentServiceTest,AgentControllerTest'`
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/agent/AgentService.java \
        backend/src/main/java/com/softmatrix/portal/agent/AgentController.java \
        backend/src/test/java/com/softmatrix/portal/agent/AgentServiceTest.java \
        backend/src/test/java/com/softmatrix/portal/agent/AgentControllerTest.java
git commit -m "backend: add GET /api/agents/{id}"
```

---

### Task DE-T3: 后端 · `GET /api/config` 配置下发端点

浏览器可达的设计器地址与后端内网调用地址在生产环境常常不同,故独立配置项 `flowise.designer-base-url`,缺省回落到 `flowise.base-url`。

**Files:**
- Create: `backend/src/main/java/com/softmatrix/portal/config/ConfigController.java`
- Modify: `backend/src/main/resources/application.yml`(flowise 段)
- Test: `backend/src/test/java/com/softmatrix/portal/config/ConfigControllerTest.java`(新建)

- [ ] **Step 1: 写失败的测试**

新建 `ConfigControllerTest.java`:

```java
package com.softmatrix.portal.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfigController.class)
@Import({SecurityConfig.class, com.softmatrix.portal.TestOAuth2Config.class})
@TestPropertySource(properties = {
        "flowise.base-url=http://internal:3000",
        "flowise.designer-base-url=http://designer.example:3000"})
class ConfigControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void requires_auth() throws Exception {
        mvc.perform(get("/api/config")).andExpect(status().isUnauthorized());
    }

    @Test
    void returns_designer_base_url() throws Exception {
        mvc.perform(get("/api/config").with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.designerBaseUrl").value("http://designer.example:3000"));
    }
}
```

- [ ] **Step 2: 跑测试确认编译失败(ConfigController 不存在)**

Run: `cd backend && mvn -q test -Dtest=ConfigControllerTest`
Expected: COMPILE FAIL —— `cannot find symbol: class ConfigController`。

- [ ] **Step 3: 实现 ConfigController 并补 application.yml**

新建 `ConfigController.java`:

```java
package com.softmatrix.portal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigController {

    private final String designerBaseUrl;

    public ConfigController(
            @Value("${flowise.designer-base-url:${flowise.base-url}}") String designerBaseUrl) {
        this.designerBaseUrl = designerBaseUrl;
    }

    public record AppConfig(String designerBaseUrl) {}

    /** 下发浏览器侧需要的运行时配置。designerBaseUrl 须是浏览器可达地址。 */
    @GetMapping("/api/config")
    public AppConfig config() {
        return new AppConfig(designerBaseUrl);
    }
}
```

`application.yml` 的 flowise 段改为:

```yaml
flowise:
  base-url: http://localhost:3000
  # 浏览器可达的设计器地址;生产环境与 base-url(内网)常常不同,缺省回落到 base-url
  designer-base-url: http://localhost:3000
  api-key: portal-flowise-key
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q test -Dtest=ConfigControllerTest`
Expected: PASS(2 个用例)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/config/ConfigController.java \
        backend/src/main/resources/application.yml \
        backend/src/test/java/com/softmatrix/portal/config/ConfigControllerTest.java
git commit -m "backend: add GET /api/config exposing designer base url"
```

---

### Task DE-T4: 前端 · 类型与 API 客户端

**Files:**
- Modify: `frontend/src/api/types.ts`(文件末尾追加)
- Create: `frontend/src/api/config.ts`
- Modify: `frontend/src/api/agents.ts`(`listAgents` 之后插入)

- [ ] **Step 1: types.ts 追加 AppConfig**

```ts
// GET /api/config 下发的运行时配置
export interface AppConfig {
  designerBaseUrl: string;
}
```

- [ ] **Step 2: 新建 api/config.ts**

```ts
import { client } from './client';
import type { AppConfig } from './types';

export async function getConfig(): Promise<AppConfig> {
  const { data } = await client.get('/api/config');
  return data;
}
```

- [ ] **Step 3: agents.ts 追加 getAgent**

在 `listAgents` 之后插入:

```ts
export async function getAgent(id: string): Promise<Agent> {
  const { data } = await client.get(`/api/agents/${id}`);
  return data;
}
```

- [ ] **Step 4: 构建验证**

Run: `cd frontend && npm run build`
Expected: PASS(tsc + vite 无报错)。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/types.ts frontend/src/api/config.ts frontend/src/api/agents.ts
git commit -m "frontend: add config api and getAgent client"
```

---

### Task DE-T5: 前端 · DesignerPage 与路由

沉浸式布局(设计确认的布局 A):设计器路由**不套** AppLayout,细顶栏 + iframe 占满视口。同时把 `STATUS_LABEL` 从 AgentListPage 抽到共享模块,避免两页重复。

**Files:**
- Create: `frontend/src/pages/agentStatus.ts`
- Create: `frontend/src/pages/DesignerPage.tsx`
- Modify: `frontend/src/pages/AgentListPage.tsx`(删除本地 STATUS_LABEL,改 import)
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: 新建 agentStatus.ts(STATUS_LABEL 抽出)**

```ts
import type { AgentStatus } from '../api/types';

export const STATUS_LABEL: Record<AgentStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  PUBLISHED: { text: '已发布', color: 'green' },
  DISABLED: { text: '已停用', color: 'red' },
};
```

- [ ] **Step 2: AgentListPage 改用共享 STATUS_LABEL**

删除 `AgentListPage.tsx` 顶部的本地 `STATUS_LABEL` 常量(现第 13-17 行),在 import 区追加:

```ts
import { STATUS_LABEL } from './agentStatus';
```

- [ ] **Step 3: 新建 DesignerPage.tsx**

```tsx
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Result, Space, Spin, Tag, Typography, message } from 'antd';
import type { Agent, AppConfig } from '../api/types';
import { getAgent, withdrawAgent } from '../api/agents';
import { getConfig } from '../api/config';
import { STATUS_LABEL } from './agentStatus';

/** 沉浸式设计器页:不套 AppLayout,细顶栏 + iframe 占满视口。 */
export default function DesignerPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [agent, setAgent] = useState<Agent | null>(null);
  const [config, setConfig] = useState<AppConfig | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([getAgent(id), getConfig()])
      .then(([a, c]) => { setAgent(a); setConfig(c); })
      .catch((e) => {
        if (e.response?.status === 404) setNotFound(true);
        else message.error('加载失败');
      })
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const doWithdraw = async () => {
    try { await withdrawAgent(id!); message.success('已撤回为草稿'); load(); }
    catch (e: any) { message.error(e.response?.data?.message ?? '操作失败'); }
  };

  if (loading) return <Spin style={{ marginTop: '20vh', display: 'block' }} />;

  if (notFound || !agent || !config) {
    return (
      <Result status="404" title="Agent 不存在"
        extra={<Button type="primary" onClick={() => navigate('/')}>返回列表</Button>} />
    );
  }

  if (agent.status === 'PUBLISHED') {
    return (
      <Result status="warning" title="该 Agent 已发布"
        subTitle="流程修改保存后立即生效,会直接影响线上对话;请先撤回为草稿再编排。"
        extra={
          <Space>
            <Button type="primary" onClick={doWithdraw}>撤回为草稿</Button>
            <Button onClick={() => navigate('/')}>返回列表</Button>
          </Space>
        } />
    );
  }

  const label = STATUS_LABEL[agent.status];
  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 12,
        padding: '8px 16px', borderBottom: '1px solid #f0f0f0',
      }}>
        <Button size="small" onClick={() => navigate('/')}>← 返回</Button>
        <Typography.Text strong>{agent.name}</Typography.Text>
        <Tag color={label.color}>{label.text}</Tag>
        <Typography.Text type="secondary">在画布中保存后立即生效</Typography.Text>
      </div>
      <iframe
        title="Flowise 设计器"
        src={`${config.designerBaseUrl}/canvas/${agent.flowiseChatflowId}`}
        style={{ flex: 1, border: 'none', width: '100%' }}
      />
    </div>
  );
}
```

- [ ] **Step 4: App.tsx 路由重构(设计器路由不套 AppLayout)**

将 `App.tsx` 的 return 块替换为:

```tsx
  return (
    <Routes>
      <Route path="/agents/:id/design" element={<DesignerPage />} />
      <Route path="/" element={<AppLayout user={user}><AgentListPage /></AppLayout>} />
      <Route path="/agents/:id/chat" element={<AppLayout user={user}><ChatPage /></AppLayout>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
```

并在 import 区追加:

```ts
import DesignerPage from './pages/DesignerPage';
```

- [ ] **Step 5: 构建验证**

Run: `cd frontend && npm run build`
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/agentStatus.ts frontend/src/pages/DesignerPage.tsx \
        frontend/src/pages/AgentListPage.tsx frontend/src/App.tsx
git commit -m "frontend: add immersive designer page embedding flowise canvas"
```

---

### Task DE-T6: 前端 · 列表"编排"入口与新建表单高级选项

**Files:**
- Modify: `frontend/src/pages/AgentListPage.tsx`(`actionsFor` 与新建/编辑 Modal)

- [ ] **Step 1: actionsFor 增加"编排"按钮**

`actionsFor` 中现有的 DRAFT/DISABLED 分支(发布按钮所在)改为在**发布按钮之前**先推入"编排":

```tsx
    if (a.status === 'DRAFT' || a.status === 'DISABLED') {
      btns.push(<Button key="design" size="small" type="link" onClick={() => navigate(`/agents/${a.id}/design`)}>编排</Button>);
      btns.push(<Button key="pub" size="small" type="link" onClick={() => runAction(() => publishAgent(a.id), a.status === 'DISABLED' ? '已重新启用' : '已发布')}>{a.status === 'DISABLED' ? '重新启用' : '发布'}</Button>);
    }
```

- [ ] **Step 2: 新建表单的 Chatflow ID 改为折叠高级选项**

antd import 行追加 `Collapse`。将 Modal 内现有的:

```tsx
          {!editing && (
            <Form.Item name="flowiseChatflowId" label="Flowise Chatflow ID"
              rules={[{ required: true, max: 64 }]}>
              <Input placeholder="从 Flowise 复制的 Chatflow ID" />
            </Form.Item>
          )}
```

替换为(去掉 `required`,留空即自动建流):

```tsx
          {!editing && (
            <Collapse ghost items={[{
              key: 'advanced',
              label: '高级选项:绑定已有 Chatflow',
              children: (
                <Form.Item name="flowiseChatflowId" label="Flowise Chatflow ID"
                  rules={[{ max: 64 }]}
                  extra="留空将自动在 Flowise 创建空白流程">
                  <Input placeholder="从 Flowise 复制的 Chatflow ID" />
                </Form.Item>
              ),
            }]} />
          )}
```

- [ ] **Step 3: 构建验证**

Run: `cd frontend && npm run build`
Expected: PASS。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/AgentListPage.tsx
git commit -m "frontend: add design entry on agent list and optional chatflow binding"
```

---

### Task DE-T7: 人工端到端验证(真实环境)

**前置:** `cd infra && docker compose up -d`(Postgres + Keycloak + Flowise);后端 `FLOWISE_API_KEY=<真实key> mvn spring-boot:run`(真实 key 在 Flowise UI 的 Settings → API Keys 查看,**不要提交进仓库**);前端 `npm run dev`。Flowise 设计账号:`admin@softmatrix.local`(密码见团队内部记录)。

- [ ] **Step 1: 全量单测与构建**

Run: `cd backend && mvn -q test` 与 `cd frontend && npm run build`
Expected: 全部 PASS。

- [ ] **Step 2: 新建 Agent(留空自动建流)**

登录门户 → 新建 Agent,只填名称,不展开高级选项 → 保存。
Expected: 创建成功,列表出现 DRAFT 行;到 Flowise UI(或 `GET /api/v1/chatflows`)确认新增了与 Agent 同名的空白 Chatflow。

- [ ] **Step 3: 进入设计器并首次登录**

列表点"编排" → 进入 `/agents/{id}/design`。
Expected: 沉浸式页面(无门户页头),细顶栏显示名称 + 草稿标签;iframe 内出现 Flowise 登录页,用设计账号登录后显示该 Agent 的空白画布(URL 为 `/canvas/{chatflowId}`)。

- [ ] **Step 4: 编排并保存**

画布中添加最小可运行链(Chat 模型节点,沿用切片一的 Azure OpenAI 凭据)→ 保存。
Expected: Flowise 保存成功提示;刷新设计器页画布内容仍在(即改动确实落库)。

- [ ] **Step 5: 发布并 Chat 验证**

返回列表 → 发布该 Agent → 打开 Chat 发消息。
Expected: 流式回复正常,证明编排的流真实生效。

- [ ] **Step 6: 验证 PUBLISHED 拦截页**

直接访问该(已发布)Agent 的 `/agents/{id}/design`。
Expected: 不渲染 iframe,出现"该 Agent 已发布"警示页;点"撤回为草稿"后原地进入画布;列表中已发布行无"编排"按钮。

- [ ] **Step 7: 验证高级选项(绑定已有流)**

新建 Agent,展开高级选项,填入 Step 4 那个 Chatflow 的 ID → 保存。
Expected: 创建成功且绑定该流;填一个不存在的 ID 则报 400"指定的 Chatflow 不存在"。

- [ ] **Step 8: 会话过期路径(抽查)**

浏览器删除 Flowise 域下的 Cookie 后刷新设计器页。
Expected: iframe 内重现 Flowise 登录页,登录后恢复画布,门户侧无需任何操作。

- [ ] **Step 9: Commit(如有 E2E 修复)**

```bash
git add -A && git commit -m "chore: e2e wiring fixes for designer embed"
```
仅在 E2E 过程确有代码修复时提交;修复需先补相应单测(参照子项目二 `createChatflow_sends_type_field` 的做法)。

---

## 自查记录(写完计划后)

- **规格覆盖:** §4.1 自动建流→DE-T1;§4.2 config 端点→DE-T3;§5.1 列表入口/高级选项→DE-T6;§5.2 设计器页(含 PUBLISHED 拦截/404)→DE-T5(依赖 DE-T2 的单查端点);§6 错误处理→DE-T1(502)/DE-T5(404、拦截页);§7 测试→各任务 TDD 步骤 + DE-T7。无缺口。
- **占位符扫描:** 无 TBD/TODO;所有代码步骤含完整代码。
- **类型一致性:** `service.get(UUID)`(DE-T2)与控制器/测试一致;`AppConfig.designerBaseUrl` 后端 record 与前端 interface 字段名一致;`STATUS_LABEL` 抽出后 DE-T5/DE-T6 均从 `./agentStatus` 导入;`getAgent`/`getConfig` 签名在 DE-T4/DE-T5 一致。
