# Agent 管理加深(子项目二)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Agent 加上分类、标签、三态生命周期(草稿/已发布/已停用,仅已发布可运行)、按分类/标签/状态/关键字筛选,以及元数据+Flowise 流定义的导入导出;并引入 Flyway 做受控 schema 迁移。

**Architecture:** 就地扩展现有 `agent` 模块(方案 A):`agent` 表加列,`AgentService` 承载状态机与导入导出,`FlowiseClient` 扩展取流/建流方法,`ChatController` 增加发布门控。schema 由 Flyway 迁移管理(`ddl-auto` 改为 `validate`)。仓库层/迁移用 Testcontainers 真 PostgreSQL 测试,其余为纯单测 + MockWebServer + WebMvc 切片测试。

**Tech Stack:** Spring Boot 3.3(Java 17)、Spring Data JPA + Hibernate 6、Flyway、Testcontainers(PostgreSQL)、React 18 + TypeScript + Ant Design 5。

---

## 文件结构

**后端**
```
backend/
├── pom.xml                                   # 加 flyway、testcontainers 依赖
├── src/main/resources/
│   ├── application.yml                       # ddl-auto: validate + flyway 配置
│   └── db/migration/
│       ├── V1__baseline.sql                  # 切片一 agent 表基线
│       └── V2__agent_management.sql          # 加列 + 存量置为 PUBLISHED
└── src/main/java/com/softmatrix/portal/
    ├── agent/
    │   ├── AgentStatus.java                   # 新增:枚举 DRAFT/PUBLISHED/DISABLED
    │   ├── AgentEntity.java                   # 改:加 category/tags/status/publishedAt
    │   ├── AgentRepository.java               # 改:search + distinct categories/tags
    │   ├── AgentService.java                  # 改:元数据、状态机、筛选、导入导出
    │   ├── AgentController.java               # 改:筛选、categories/tags、状态转换、导入导出
    │   └── dto/
    │       ├── AgentRequest.java              # 改:加 category/tags,flowiseChatflowId 可空
    │       ├── AgentResponse.java             # 改:加 category/tags/status/publishedAt
    │       └── AgentPackage.java              # 新增:导入导出打包结构
    └── chat/
        ├── FlowiseClient.java                 # 改:getChatflow / createChatflow
        └── ChatController.java                # 改:运行前门控 status==PUBLISHED
```

**前端**
```
frontend/src/
├── api/types.ts          # 改:Agent/AgentRequest 加字段;AgentStatus
├── api/agents.ts         # 改:筛选参数、状态转换、导入导出、categories/tags
├── pages/AgentListPage.tsx  # 改:筛选栏、状态列、动态操作、分类标签表单、导入导出
└── pages/ChatPage.tsx    # 改:409 提示未发布
```

**约定:** 所有后端命令在 `backend/` 下运行。若 Maven 因 Homebrew JDK 25 导致 Mockito 报错,pom 已含 `-Dnet.bytebuddy.experimental=true`;如仍需在 JDK 17 跑,`export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`。Testcontainers 测试需要 Docker daemon 运行。

---

## Phase 1 — 迁移与数据模型

### Task 1: 引入 Flyway 依赖与配置

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 加 Flyway 依赖**

在 `backend/pom.xml` 的 `<dependencies>` 中,`spring-boot-starter-validation` 依赖块之后插入:

```xml
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
```

- [ ] **Step 2: 改 application.yml 的 JPA/Flyway 配置**

将 `backend/src/main/resources/application.yml` 中的 `jpa` 段:

```yaml
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
```

替换为(改为 `validate`,并加 Flyway 配置):

```yaml
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 1
```

> `baseline-on-migrate: true`:对切片一已存在 `agent` 表的库,Flyway 将其基线在 V1(跳过 V1、执行 V2+);对全新空库(如 Testcontainers),从 V1 起全部执行。

- [ ] **Step 3: 验证编译**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: 成功。

- [ ] **Step 4: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml
git commit -m "backend: add flyway and switch ddl-auto to validate"
```

---

### Task 2: Flyway 迁移脚本

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__baseline.sql`
- Create: `backend/src/main/resources/db/migration/V2__agent_management.sql`

- [ ] **Step 1: 编写 V1 基线(与切片一实体一致)**

```sql
-- V1__baseline.sql — 切片一 agent 表基线(全新库上创建;已有库上被 Flyway 基线跳过)
CREATE TABLE IF NOT EXISTS agent (
    id                  uuid PRIMARY KEY,
    name                varchar(100) NOT NULL,
    description         text,
    flowise_chatflow_id varchar(64) NOT NULL,
    owner               varchar(100) NOT NULL,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL
);
```

- [ ] **Step 2: 编写 V2(加列 + 存量置为 PUBLISHED)**

```sql
-- V2__agent_management.sql — Agent 管理加深:分类/标签/状态/发布时间
ALTER TABLE agent ADD COLUMN category     varchar(50);
ALTER TABLE agent ADD COLUMN tags         text[] NOT NULL DEFAULT '{}';
ALTER TABLE agent ADD COLUMN status       varchar(16) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE agent ADD COLUMN published_at timestamptz;

-- 存量 Agent(切片一已能运行)一次性置为 PUBLISHED,避免破坏演示
UPDATE agent SET status = 'PUBLISHED', published_at = now();
```

- [ ] **Step 3: 验证迁移在真库上跑通**

前置:`cd infra && docker compose up -d`(Postgres 已在 `:5432`)。为得到干净结果,重置 portal 库的 flyway 状态与列(仅本地开发):

Run:
```bash
cd backend && mvn -q spring-boot:run
```
Expected: 启动日志出现 `Flyway ... Successfully applied` 或 `Successfully validated`;应用启动无异常(`Started PortalApplication`)。按 Ctrl-C 停止。

> 若本地 portal 库是切片一遗留(已有 agent 表、无 flyway_schema_history):Flyway 会基线在 V1 并执行 V2,存量行被置为 PUBLISHED。若想完全从零:`cd infra && docker compose down -v && docker compose up -d`。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/
git commit -m "backend: add flyway migrations for agent management columns"
```

---

### Task 3: AgentStatus 枚举与实体扩展

**Files:**
- Create: `backend/src/main/java/com/softmatrix/portal/agent/AgentStatus.java`
- Modify: `backend/src/main/java/com/softmatrix/portal/agent/AgentEntity.java`

- [ ] **Step 1: 编写状态枚举(含合法转换规则)**

```java
// agent/AgentStatus.java
package com.softmatrix.portal.agent;

import java.util.Set;

public enum AgentStatus {
    DRAFT,
    PUBLISHED,
    DISABLED;

    /** 目标状态 -> 允许的来源状态集合。 */
    public boolean canTransitionTo(AgentStatus target) {
        return switch (target) {
            case PUBLISHED -> this == DRAFT || this == DISABLED; // 发布 / 重新启用
            case DISABLED  -> this == PUBLISHED;                 // 停用
            case DRAFT     -> this == PUBLISHED;                 // 撤回
        };
    }

    static Set<AgentStatus> all() { return Set.of(DRAFT, PUBLISHED, DISABLED); }
}
```

- [ ] **Step 2: 扩展 AgentEntity**

在 `AgentEntity.java` 中,`owner` 字段之后、`created_at` 字段之前插入四个新字段:

```java
    @Column(length = 50)
    private String category;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] tags = new String[0];

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentStatus status = AgentStatus.DRAFT;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
```

并在 getters/setters 区(`getOwner`/`setOwner` 之后)加入:

```java
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags == null ? new String[0] : tags; }
    public AgentStatus getStatus() { return status; }
    public void setStatus(AgentStatus status) { this.status = status; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
```

- [ ] **Step 3: 验证编译**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: 成功。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/agent/AgentStatus.java backend/src/main/java/com/softmatrix/portal/agent/AgentEntity.java
git commit -m "backend: add agent status enum and metadata fields"
```

---

## Phase 2 — DTO、状态机与筛选(Service)

### Task 4: 扩展 DTO

**Files:**
- Modify: `backend/src/main/java/com/softmatrix/portal/agent/dto/AgentRequest.java`
- Modify: `backend/src/main/java/com/softmatrix/portal/agent/dto/AgentResponse.java`

- [ ] **Step 1: 扩展 AgentRequest(flowiseChatflowId 改为可空,加 category/tags)**

整体替换 `AgentRequest.java`:

```java
package com.softmatrix.portal.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AgentRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        @Size(max = 50) String category,
        List<String> tags,
        // 仅创建时使用;更新时忽略。可空,create 时在 Service 中校验非空。
        @Size(max = 64) String flowiseChatflowId
) {}
```

- [ ] **Step 2: 扩展 AgentResponse**

整体替换 `AgentResponse.java`:

```java
package com.softmatrix.portal.agent.dto;

import com.softmatrix.portal.agent.AgentEntity;
import com.softmatrix.portal.agent.AgentStatus;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record AgentResponse(
        UUID id,
        String name,
        String description,
        String category,
        List<String> tags,
        String flowiseChatflowId,
        AgentStatus status,
        String owner,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AgentResponse from(AgentEntity e) {
        return new AgentResponse(
                e.getId(), e.getName(), e.getDescription(), e.getCategory(),
                e.getTags() == null ? List.of() : Arrays.asList(e.getTags()),
                e.getFlowiseChatflowId(), e.getStatus(), e.getOwner(),
                e.getPublishedAt(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
```

- [ ] **Step 3: 验证编译(预期 AgentService/AgentControllerTest 暂时编译失败)**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: 成功(main 源码;`AgentService.create/update` 仍用旧字段但签名未变,能编译)。

> 说明:此步只编译 main。既有测试引用旧 `AgentResponse` 构造器,将在 Task 6/8 更新。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/agent/dto/
git commit -m "backend: extend agent DTOs with category, tags, status"
```

---

### Task 5: Service — 创建/更新元数据 + 状态机

**Files:**
- Modify: `backend/src/main/java/com/softmatrix/portal/agent/AgentService.java`
- Modify: `backend/src/test/java/com/softmatrix/portal/agent/AgentServiceTest.java`

- [ ] **Step 1: 写失败测试(状态机 + 元数据 + 创建校验)**

整体替换 `AgentServiceTest.java`:

```java
package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentRequest;
import com.softmatrix.portal.agent.dto.AgentResponse;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    AgentRepository repo;
    ChatflowValidator validator;
    AgentService service;

    @BeforeEach
    void setUp() {
        repo = mock(AgentRepository.class);
        validator = mock(ChatflowValidator.class);
        service = new AgentService(repo, validator);
        when(repo.save(any(AgentEntity.class))).thenAnswer(inv -> {
            AgentEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    private AgentEntity stored(AgentStatus status) {
        AgentEntity e = new AgentEntity();
        e.setId(UUID.randomUUID());
        e.setName("A");
        e.setFlowiseChatflowId("cf1");
        e.setOwner("admin");
        e.setStatus(status);
        when(repo.findById(e.getId())).thenReturn(java.util.Optional.of(e));
        return e;
    }

    @Test
    void create_defaults_to_draft_and_sets_metadata() {
        when(validator.chatflowExists("cf1")).thenReturn(true);
        AgentRequest req = new AgentRequest("A", "d", "客服", List.of("faq", "zh"), "cf1");

        AgentResponse res = service.create(req, "admin");

        assertThat(res.status()).isEqualTo(AgentStatus.DRAFT);
        assertThat(res.category()).isEqualTo("客服");
        assertThat(res.tags()).containsExactly("faq", "zh");
        assertThat(res.owner()).isEqualTo("admin");
    }

    @Test
    void create_rejects_blank_chatflow() {
        AgentRequest req = new AgentRequest("A", "d", null, null, "  ");
        assertThatThrownBy(() -> service.create(req, "admin"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Chatflow");
        verify(repo, never()).save(any());
    }

    @Test
    void update_ignores_chatflow_and_status() {
        AgentEntity e = stored(AgentStatus.PUBLISHED);
        e.setFlowiseChatflowId("original");
        AgentRequest req = new AgentRequest("A2", "d2", "法务", List.of("t"), "SHOULD-BE-IGNORED");

        AgentResponse res = service.update(e.getId(), req);

        assertThat(res.name()).isEqualTo("A2");
        assertThat(res.flowiseChatflowId()).isEqualTo("original"); // 未被改
        assertThat(res.status()).isEqualTo(AgentStatus.PUBLISHED);  // 未被改
    }

    @Test
    void publish_from_draft_sets_published_at() {
        AgentEntity e = stored(AgentStatus.DRAFT);
        AgentResponse res = service.publish(e.getId());
        assertThat(res.status()).isEqualTo(AgentStatus.PUBLISHED);
        assertThat(res.publishedAt()).isNotNull();
    }

    @Test
    void disable_from_draft_is_illegal() {
        AgentEntity e = stored(AgentStatus.DRAFT);
        assertThatThrownBy(() -> service.disable(e.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_TRANSITION");
    }

    @Test
    void withdraw_from_published_returns_to_draft() {
        AgentEntity e = stored(AgentStatus.PUBLISHED);
        assertThat(service.withdraw(e.getId()).status()).isEqualTo(AgentStatus.DRAFT);
    }

    @Test
    void reenable_from_disabled_to_published() {
        AgentEntity e = stored(AgentStatus.DISABLED);
        assertThat(service.publish(e.getId()).status()).isEqualTo(AgentStatus.PUBLISHED);
    }
}
```

> 注:`hasFieldOrPropertyWithValue("code", ...)` 依赖 `ApiException.getCode()`(已存在)。

- [ ] **Step 2: 运行测试,确认失败**

Run: `cd backend && mvn -q test -Dtest=AgentServiceTest`
Expected: 编译失败(`service.publish/disable/withdraw` 不存在)。

- [ ] **Step 3: 改写 AgentService**

整体替换 `AgentService.java`:

```java
package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentRequest;
import com.softmatrix.portal.agent.dto.AgentResponse;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AgentService {

    private final AgentRepository repo;
    private final ChatflowValidator validator;

    public AgentService(AgentRepository repo, ChatflowValidator validator) {
        this.repo = repo;
        this.validator = validator;
    }

    public List<AgentResponse> list(String category, String status, String keyword, String tag) {
        AgentStatus st = parseStatus(status);
        return repo.search(emptyToNull(category), st == null ? null : st.name(),
                        emptyToNull(keyword), emptyToNull(tag))
                .stream().map(AgentResponse::from).toList();
    }

    public List<String> listCategories() { return repo.findDistinctCategories(); }

    public List<String> listTags() { return repo.findDistinctTags(); }

    public AgentResponse create(AgentRequest req, String owner) {
        if (req.flowiseChatflowId() == null || req.flowiseChatflowId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHATFLOW",
                    "创建 Agent 必须提供 Chatflow ID");
        }
        requireChatflow(req.flowiseChatflowId());
        AgentEntity e = new AgentEntity();
        e.setName(req.name());
        e.setDescription(req.description());
        e.setCategory(req.category());
        e.setTags(toArray(req.tags()));
        e.setFlowiseChatflowId(req.flowiseChatflowId());
        e.setOwner(owner);
        e.setStatus(AgentStatus.DRAFT);
        return AgentResponse.from(repo.save(e));
    }

    /** 更新只改元数据;忽略 flowiseChatflowId 与 status。 */
    public AgentResponse update(UUID id, AgentRequest req) {
        AgentEntity e = find(id);
        e.setName(req.name());
        e.setDescription(req.description());
        e.setCategory(req.category());
        e.setTags(toArray(req.tags()));
        return AgentResponse.from(repo.save(e));
    }

    public void delete(UUID id) { repo.delete(find(id)); }

    public AgentResponse publish(UUID id)  { return transition(id, AgentStatus.PUBLISHED); }
    public AgentResponse disable(UUID id)  { return transition(id, AgentStatus.DISABLED); }
    public AgentResponse withdraw(UUID id) { return transition(id, AgentStatus.DRAFT); }

    private AgentResponse transition(UUID id, AgentStatus target) {
        AgentEntity e = find(id);
        if (!e.getStatus().canTransitionTo(target)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSITION",
                    "不允许从 " + e.getStatus() + " 转换到 " + target);
        }
        e.setStatus(target);
        if (target == AgentStatus.PUBLISHED) {
            e.setPublishedAt(OffsetDateTime.now());
        }
        return AgentResponse.from(repo.save(e));
    }

    public AgentEntity find(UUID id) {
        return repo.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent 不存在"));
    }

    void requireChatflow(String chatflowId) {
        if (!validator.chatflowExists(chatflowId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHATFLOW",
                    "指定的 Chatflow 不存在,请检查 Chatflow ID");
        }
    }

    private static String[] toArray(List<String> tags) {
        return tags == null ? new String[0] : tags.toArray(new String[0]);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static AgentStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return AgentStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "未知状态: " + status);
        }
    }
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `cd backend && mvn -q test -Dtest=AgentServiceTest`
Expected: PASS(7 个测试)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/agent/AgentService.java backend/src/test/java/com/softmatrix/portal/agent/AgentServiceTest.java
git commit -m "backend: add agent metadata update and status machine"
```

---

### Task 6: Repository — 筛选与去重查询(Testcontainers)

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/java/com/softmatrix/portal/agent/AgentRepository.java`
- Test: `backend/src/test/java/com/softmatrix/portal/agent/AgentRepositoryTest.java`

- [ ] **Step 1: 加 Testcontainers 依赖**

在 `backend/pom.xml` 的测试依赖区(`h2` 依赖块之后)插入:

```xml
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-testcontainers</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
```

- [ ] **Step 2: 扩展 AgentRepository**

整体替换 `AgentRepository.java`:

```java
package com.softmatrix.portal.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<AgentEntity, UUID> {

    @Query(value = """
            SELECT * FROM agent
            WHERE (:category IS NULL OR category = :category)
              AND (:status   IS NULL OR status = :status)
              AND (:keyword  IS NULL OR name ILIKE '%' || :keyword || '%')
              AND (:tag      IS NULL OR :tag = ANY(tags))
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<AgentEntity> search(@Param("category") String category,
                             @Param("status") String status,
                             @Param("keyword") String keyword,
                             @Param("tag") String tag);

    @Query(value = "SELECT DISTINCT category FROM agent WHERE category IS NOT NULL ORDER BY category",
            nativeQuery = true)
    List<String> findDistinctCategories();

    @Query(value = "SELECT DISTINCT unnest(tags) AS tag FROM agent ORDER BY tag", nativeQuery = true)
    List<String> findDistinctTags();
}
```

- [ ] **Step 3: 写 Testcontainers 仓库测试**

```java
// test/.../agent/AgentRepositoryTest.java
package com.softmatrix.portal.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AgentRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    AgentRepository repo;

    private AgentEntity agent(String name, String category, AgentStatus status, String... tags) {
        AgentEntity e = new AgentEntity();
        e.setName(name);
        e.setCategory(category);
        e.setStatus(status);
        e.setTags(tags);
        e.setFlowiseChatflowId("cf");
        e.setOwner("admin");
        return e;
    }

    @Test
    void search_filters_by_category_status_keyword_tag() {
        repo.save(agent("客服助手", "客服", AgentStatus.PUBLISHED, "faq", "zh"));
        repo.save(agent("合同审查", "法务", AgentStatus.DRAFT, "legal"));

        assertThat(repo.search("客服", null, null, null)).hasSize(1);
        assertThat(repo.search(null, "DRAFT", null, null)).hasSize(1);
        assertThat(repo.search(null, null, "合同", null)).hasSize(1);
        assertThat(repo.search(null, null, null, "faq")).hasSize(1);
        assertThat(repo.search(null, null, null, "nope")).isEmpty();
        assertThat(repo.search(null, null, null, null)).hasSize(2);
    }

    @Test
    void distinct_categories_and_tags() {
        repo.save(agent("a", "客服", AgentStatus.PUBLISHED, "faq", "zh"));
        repo.save(agent("b", "法务", AgentStatus.DRAFT, "faq"));

        assertThat(repo.findDistinctCategories()).containsExactly("客服", "法务");
        assertThat(repo.findDistinctTags()).containsExactly("faq", "zh");
    }
}
```

- [ ] **Step 4: 运行测试,确认通过(需 Docker)**

Run: `cd backend && mvn -q test -Dtest=AgentRepositoryTest`
Expected: PASS(2 个测试)。Testcontainers 会拉起临时 PostgreSQL 并跑 Flyway 迁移。

> 若报 `Could not find a valid Docker environment`,先启动 Docker Desktop。

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/softmatrix/portal/agent/AgentRepository.java backend/src/test/java/com/softmatrix/portal/agent/AgentRepositoryTest.java
git commit -m "backend: add agent search and distinct queries with testcontainers"
```

---

## Phase 3 — Controller 与门控

### Task 7: AgentController — 筛选、去重、状态转换端点

**Files:**
- Modify: `backend/src/main/java/com/softmatrix/portal/agent/AgentController.java`
- Modify: `backend/src/test/java/com/softmatrix/portal/agent/AgentControllerTest.java`

- [ ] **Step 1: 改写 AgentController**

整体替换 `AgentController.java`:

```java
package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentRequest;
import com.softmatrix.portal.agent.dto.AgentResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @GetMapping
    public List<AgentResponse> list(@RequestParam(required = false) String category,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String tag) {
        return service.list(category, status, keyword, tag);
    }

    @GetMapping("/categories")
    public List<String> categories() { return service.listCategories(); }

    @GetMapping("/tags")
    public List<String> tags() { return service.listTags(); }

    @PostMapping
    public AgentResponse create(@Valid @RequestBody AgentRequest req,
                                @AuthenticationPrincipal OidcUser user) {
        return service.create(req, user.getPreferredUsername());
    }

    @PutMapping("/{id}")
    public AgentResponse update(@PathVariable UUID id, @Valid @RequestBody AgentRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/publish")
    public AgentResponse publish(@PathVariable UUID id) { return service.publish(id); }

    @PostMapping("/{id}/disable")
    public AgentResponse disable(@PathVariable UUID id) { return service.disable(id); }

    @PostMapping("/{id}/withdraw")
    public AgentResponse withdraw(@PathVariable UUID id) { return service.withdraw(id); }
}
```

- [ ] **Step 2: 写失败测试(更新既有测试到新构造器 + 新端点)**

整体替换 `AgentControllerTest.java`:

```java
package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentResponse;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
@Import({SecurityConfig.class, com.softmatrix.portal.TestOAuth2Config.class})
class AgentControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AgentService service;
    @MockBean ChatflowValidator validator;

    private AgentResponse sample(AgentStatus status) {
        return new AgentResponse(UUID.randomUUID(), "A", "d", "客服", List.of("faq"),
                "cf1", status, "admin", null, OffsetDateTime.now(), OffsetDateTime.now());
    }

    @Test
    void list_requires_auth() throws Exception {
        mvc.perform(get("/api/agents")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_passes_filters() throws Exception {
        when(service.list("客服", "DRAFT", "k", "faq")).thenReturn(List.of(sample(AgentStatus.DRAFT)));
        mvc.perform(get("/api/agents?category=客服&status=DRAFT&keyword=k&tag=faq").with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void create_uses_username_as_owner() throws Exception {
        when(service.create(any(), eq("admin"))).thenReturn(sample(AgentStatus.DRAFT));
        mvc.perform(post("/api/agents")
                .with(oidcLogin().idToken(t -> t.claim("preferred_username", "admin")))
                .contentType("application/json")
                .content("{\"name\":\"A\",\"flowiseChatflowId\":\"cf1\",\"tags\":[\"faq\"]}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.owner").value("admin"));
    }

    @Test
    void publish_endpoint() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.publish(id)).thenReturn(sample(AgentStatus.PUBLISHED));
        mvc.perform(post("/api/agents/{id}/publish", id).with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void categories_endpoint() throws Exception {
        when(service.listCategories()).thenReturn(List.of("客服", "法务"));
        mvc.perform(get("/api/agents/categories").with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0]").value("客服"));
    }
}
```

- [ ] **Step 3: 运行测试,确认通过**

Run: `cd backend && mvn -q test -Dtest=AgentControllerTest`
Expected: PASS(5 个测试)。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/agent/AgentController.java backend/src/test/java/com/softmatrix/portal/agent/AgentControllerTest.java
git commit -m "backend: add agent filter, distinct and status endpoints"
```

---

### Task 8: Chat 运行门控(仅 PUBLISHED)

**Files:**
- Modify: `backend/src/main/java/com/softmatrix/portal/chat/ChatController.java`
- Modify: `backend/src/test/java/com/softmatrix/portal/chat/ChatControllerTest.java`

- [ ] **Step 1: 写失败测试(未发布运行返回 409)**

整体替换 `ChatControllerTest.java`:

```java
package com.softmatrix.portal.chat;

import com.softmatrix.portal.agent.AgentEntity;
import com.softmatrix.portal.agent.AgentService;
import com.softmatrix.portal.agent.AgentStatus;
import com.softmatrix.portal.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, com.softmatrix.portal.TestOAuth2Config.class})
class ChatControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AgentService agentService;
    @MockBean FlowiseClient flowiseClient;

    private AgentEntity agent(AgentStatus status) {
        AgentEntity e = new AgentEntity();
        e.setFlowiseChatflowId("cf1");
        e.setStatus(status);
        return e;
    }

    @Test
    void chat_requires_auth() throws Exception {
        mvc.perform(post("/api/agents/{id}/chat", UUID.randomUUID())
                .contentType("application/json")
                .content("{\"sessionId\":\"s1\",\"message\":\"hi\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void chat_rejected_when_not_published() throws Exception {
        UUID id = UUID.randomUUID();
        when(agentService.find(id)).thenReturn(agent(AgentStatus.DRAFT));
        mvc.perform(post("/api/agents/{id}/chat", id).with(oidcLogin())
                .contentType("application/json")
                .content("{\"sessionId\":\"s1\",\"message\":\"hi\"}"))
           .andExpect(status().isConflict());
    }

    @Test
    void chat_streams_when_published() throws Exception {
        UUID id = UUID.randomUUID();
        when(agentService.find(id)).thenReturn(agent(AgentStatus.PUBLISHED));
        when(flowiseClient.streamPrediction(eq("cf1"), eq("s1"), eq("hi")))
                .thenReturn(Flux.just("Hello"));
        mvc.perform(post("/api/agents/{id}/chat", id).with(oidcLogin())
                .contentType("application/json")
                .content("{\"sessionId\":\"s1\",\"message\":\"hi\"}"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `cd backend && mvn -q test -Dtest=ChatControllerTest`
Expected: `chat_rejected_when_not_published` 失败(当前无门控,返回 200)。

- [ ] **Step 3: 加门控**

在 `ChatController.java` 的 `chat` 方法体,`AgentEntity agent = agentService.find(id);` 之后插入门控检查。整体替换该方法:

```java
    @PostMapping(value = "/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@PathVariable UUID id, @Valid @RequestBody ChatRequest req) {
        AgentEntity agent = agentService.find(id);
        if (agent.getStatus() != com.softmatrix.portal.agent.AgentStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "AGENT_NOT_RUNNABLE",
                    "该 Agent 未发布,无法运行");
        }
        return flowiseClient.streamPrediction(
                        agent.getFlowiseChatflowId(), req.sessionId(), req.message())
                .onErrorMap(ex -> new ApiException(HttpStatus.BAD_GATEWAY,
                        "FLOWISE_ERROR", "运行失败,请重试"));
    }
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `cd backend && mvn -q test -Dtest=ChatControllerTest`
Expected: PASS(3 个测试)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/chat/ChatController.java backend/src/test/java/com/softmatrix/portal/chat/ChatControllerTest.java
git commit -m "backend: gate chat run to PUBLISHED agents (409 otherwise)"
```

---

## Phase 4 — 导入 / 导出

### Task 9: FlowiseClient — getChatflow / createChatflow

**Files:**
- Modify: `backend/src/main/java/com/softmatrix/portal/chat/FlowiseClient.java`
- Modify: `backend/src/test/java/com/softmatrix/portal/chat/FlowiseClientTest.java`

- [ ] **Step 1: 写失败测试(追加到 FlowiseClientTest)**

在 `FlowiseClientTest.java` 的最后一个 `@Test` 方法之后、类结束 `}` 之前,插入:

```java
    @Test
    void getChatflow_returns_json() {
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(200)
                .setBody("{\"id\":\"cf1\",\"name\":\"Demo\",\"flowData\":\"{\\\"nodes\\\":[]}\"}")
                .addHeader("Content-Type", "application/json"));

        com.fasterxml.jackson.databind.JsonNode node = client.getChatflow("cf1");
        assertThat(node.path("name").asText()).isEqualTo("Demo");
        assertThat(node.path("flowData").asText()).contains("nodes");
    }

    @Test
    void createChatflow_returns_new_id() {
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(200)
                .setBody("{\"id\":\"new-cf\",\"name\":\"Imported\"}")
                .addHeader("Content-Type", "application/json"));

        com.fasterxml.jackson.databind.node.TextNode flowData =
                com.fasterxml.jackson.databind.node.TextNode.valueOf("{\"nodes\":[]}");
        String id = client.createChatflow("Imported", flowData);
        assertThat(id).isEqualTo("new-cf");
    }

    @Test
    void getChatflow_throws_502_on_error() {
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(500));
        assertThatThrownBy(() -> client.getChatflow("cf1"))
                .isInstanceOf(com.softmatrix.portal.common.ApiException.class)
                .hasFieldOrPropertyWithValue("code", "FLOWISE_ERROR");
    }
```

并确保文件顶部已 `import static org.assertj.core.api.Assertions.assertThatThrownBy;`(assertj 的 `assertThat` 已 import;补充 `assertThatThrownBy`):在现有 `import static org.assertj.core.api.Assertions.assertThat;` 一行下方加:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `cd backend && mvn -q test -Dtest=FlowiseClientTest`
Expected: 编译失败(`getChatflow`/`createChatflow` 不存在)。

- [ ] **Step 3: 给 FlowiseClient 加两个方法**

在 `FlowiseClient.java` 的 `streamPrediction` 方法之后、`extractToken` 之前插入:

```java
    /** 取 Chatflow 完整定义(含 flowData),供导出。失败映射为 502。 */
    public JsonNode getChatflow(String chatflowId) {
        try {
            String body = webClient.get()
                    .uri("/api/v1/chatflows/{id}", chatflowId)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new com.softmatrix.portal.common.ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "FLOWISE_ERROR", "读取 Flowise 流失败");
        }
    }

    /** 在 Flowise 新建 Chatflow,返回新 id,供导入。失败映射为 502。 */
    public String createChatflow(String name, JsonNode flowData) {
        try {
            java.util.Map<String, Object> req = new java.util.HashMap<>();
            req.put("name", name);
            req.put("flowData", flowData);
            String body = webClient.post()
                    .uri("/api/v1/chatflows")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return mapper.readTree(body).path("id").asText();
        } catch (Exception e) {
            throw new com.softmatrix.portal.common.ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "FLOWISE_ERROR", "在 Flowise 新建流失败");
        }
    }
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `cd backend && mvn -q test -Dtest=FlowiseClientTest`
Expected: PASS(原 3 + 新 3 = 6 个测试)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/chat/FlowiseClient.java backend/src/test/java/com/softmatrix/portal/chat/FlowiseClientTest.java
git commit -m "backend: add flowise getChatflow and createChatflow"
```

---

### Task 10: 导入 / 导出(打包 DTO + Service + Controller)

**Files:**
- Create: `backend/src/main/java/com/softmatrix/portal/agent/dto/AgentPackage.java`
- Modify: `backend/src/main/java/com/softmatrix/portal/agent/AgentService.java`
- Modify: `backend/src/main/java/com/softmatrix/portal/agent/AgentController.java`
- Test: `backend/src/test/java/com/softmatrix/portal/agent/AgentImportExportTest.java`

- [ ] **Step 1: 打包 DTO**

```java
// agent/dto/AgentPackage.java
package com.softmatrix.portal.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record AgentPackage(
        String softmatrixVersion,
        AgentMeta agent,
        FlowMeta flow
) {
    public record AgentMeta(String name, String description, String category, List<String> tags) {}
    public record FlowMeta(String name, JsonNode flowData) {}
}
```

- [ ] **Step 2: 写失败测试**

```java
// test/.../agent/AgentImportExportTest.java
package com.softmatrix.portal.agent;

import com.fasterxml.jackson.databind.node.TextNode;
import com.softmatrix.portal.agent.dto.AgentPackage;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.chat.FlowiseClient;
import com.softmatrix.portal.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentImportExportTest {

    AgentRepository repo;
    ChatflowValidator validator;
    FlowiseClient flowise;
    AgentService service;

    @BeforeEach
    void setUp() {
        repo = mock(AgentRepository.class);
        validator = mock(ChatflowValidator.class);
        flowise = mock(FlowiseClient.class);
        service = new AgentService(repo, validator, flowise);
        when(repo.save(any(AgentEntity.class))).thenAnswer(inv -> {
            AgentEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @Test
    void export_packs_metadata_and_flow() {
        AgentEntity e = new AgentEntity();
        e.setId(UUID.randomUUID());
        e.setName("客服"); e.setDescription("d"); e.setCategory("客服");
        e.setTags(new String[]{"faq"}); e.setFlowiseChatflowId("cf1");
        when(repo.findById(e.getId())).thenReturn(Optional.of(e));
        com.fasterxml.jackson.databind.node.ObjectNode cf =
                com.fasterxml.jackson.databind.json.JsonMapper.builder().build().createObjectNode();
        cf.put("name", "Demo");
        cf.set("flowData", TextNode.valueOf("{\"nodes\":[]}"));
        when(flowise.getChatflow("cf1")).thenReturn(cf);

        AgentPackage pkg = service.export(e.getId());

        assertThat(pkg.agent().name()).isEqualTo("客服");
        assertThat(pkg.agent().tags()).containsExactly("faq");
        assertThat(pkg.flow().name()).isEqualTo("Demo");
        assertThat(pkg.flow().flowData().asText()).contains("nodes");
    }

    @Test
    void import_creates_flow_and_draft_agent() {
        AgentPackage.AgentMeta meta = new AgentPackage.AgentMeta("导入的", "d", "市场", List.of("gen"));
        AgentPackage.FlowMeta flow = new AgentPackage.FlowMeta("流", TextNode.valueOf("{\"nodes\":[]}"));
        AgentPackage pkg = new AgentPackage("1", meta, flow);
        when(flowise.createChatflow(eq("流"), any())).thenReturn("new-cf");

        var res = service.importPackage(pkg, "admin");

        assertThat(res.status()).isEqualTo(AgentStatus.DRAFT);
        assertThat(res.flowiseChatflowId()).isEqualTo("new-cf");
        assertThat(res.owner()).isEqualTo("admin");
        assertThat(res.category()).isEqualTo("市场");
    }

    @Test
    void import_rejects_missing_name_or_flow() {
        AgentPackage bad = new AgentPackage("1",
                new AgentPackage.AgentMeta(null, null, null, null), null);
        assertThatThrownBy(() -> service.importPackage(bad, "admin"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "IMPORT_INVALID");
    }
}
```

> 注意:测试用 `new AgentService(repo, validator, flowise)`——需把 `FlowiseClient` 加入 `AgentService` 构造器(下一步)。

- [ ] **Step 3: 运行测试,确认失败**

Run: `cd backend && mvn -q test -Dtest=AgentImportExportTest`
Expected: 编译失败(三参构造器与 export/importPackage 不存在)。

- [ ] **Step 4: 给 AgentService 加 FlowiseClient 依赖与导入导出方法**

在 `AgentService.java` 中做三处修改:

(a) 字段与构造器 —— 将现有构造器整体替换为(新增 `FlowiseClient` 参数与字段):

```java
    private final AgentRepository repo;
    private final ChatflowValidator validator;
    private final com.softmatrix.portal.chat.FlowiseClient flowise;

    public AgentService(AgentRepository repo, ChatflowValidator validator,
                        com.softmatrix.portal.chat.FlowiseClient flowise) {
        this.repo = repo;
        this.validator = validator;
        this.flowise = flowise;
    }
```

(b) 在 `find(...)` 方法之后插入导入导出方法:

```java
    public com.softmatrix.portal.agent.dto.AgentPackage export(UUID id) {
        AgentEntity e = find(id);
        com.fasterxml.jackson.databind.JsonNode cf = flowise.getChatflow(e.getFlowiseChatflowId());
        var agentMeta = new com.softmatrix.portal.agent.dto.AgentPackage.AgentMeta(
                e.getName(), e.getDescription(), e.getCategory(),
                e.getTags() == null ? java.util.List.of() : java.util.Arrays.asList(e.getTags()));
        var flowMeta = new com.softmatrix.portal.agent.dto.AgentPackage.FlowMeta(
                cf.path("name").asText(e.getName()), cf.path("flowData"));
        return new com.softmatrix.portal.agent.dto.AgentPackage("1", agentMeta, flowMeta);
    }

    public AgentResponse importPackage(com.softmatrix.portal.agent.dto.AgentPackage pkg, String owner) {
        if (pkg.agent() == null || pkg.agent().name() == null || pkg.agent().name().isBlank()
                || pkg.flow() == null || pkg.flow().flowData() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_INVALID",
                    "导入文件结构非法:缺少 agent.name 或 flow.flowData");
        }
        String flowName = pkg.flow().name() == null ? pkg.agent().name() : pkg.flow().name();
        String newChatflowId = flowise.createChatflow(flowName, pkg.flow().flowData());

        AgentEntity e = new AgentEntity();
        e.setName(pkg.agent().name());
        e.setDescription(pkg.agent().description());
        e.setCategory(pkg.agent().category());
        e.setTags(pkg.agent().tags() == null ? new String[0]
                : pkg.agent().tags().toArray(new String[0]));
        e.setFlowiseChatflowId(newChatflowId);
        e.setOwner(owner);
        e.setStatus(AgentStatus.DRAFT);
        return AgentResponse.from(repo.save(e));
    }
```

需要在 `AgentService.java` 顶部补充 import(若尚无):

```java
import java.util.Arrays;
```

- [ ] **Step 5: 修正 AgentServiceTest 以匹配三参构造器**

因构造器新增 `FlowiseClient` 参数,Task 5 的 `AgentServiceTest` 会编译失败。更新其 `setUp()`——在字段区加入:

```java
    com.softmatrix.portal.chat.FlowiseClient flowise;
```

并把:

```java
        service = new AgentService(repo, validator);
```

改为:

```java
        flowise = mock(com.softmatrix.portal.chat.FlowiseClient.class);
        service = new AgentService(repo, validator, flowise);
```

(Task 7 的 `AgentControllerTest`、Task 8 的 `ChatControllerTest` 用 `@MockBean`,不受影响。)

- [ ] **Step 6: 给 AgentController 加导入导出端点**

在 `AgentController.java` 的 `withdraw` 方法之后插入:

```java
    @GetMapping("/{id}/export")
    public org.springframework.http.ResponseEntity<com.softmatrix.portal.agent.dto.AgentPackage>
            export(@PathVariable UUID id) {
        com.softmatrix.portal.agent.dto.AgentPackage pkg = service.export(id);
        String filename = "softmatrix-agent-" + id + ".json";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(pkg);
    }

    @PostMapping("/import")
    public AgentResponse importAgent(
            @RequestBody com.softmatrix.portal.agent.dto.AgentPackage pkg,
            @AuthenticationPrincipal OidcUser user) {
        return service.importPackage(pkg, user.getPreferredUsername());
    }
```

- [ ] **Step 7: 运行导入导出与既有 Service 测试,确认通过**

Run: `cd backend && mvn -q test -Dtest=AgentImportExportTest,AgentServiceTest`
Expected: PASS(3 + 7 = 10 个测试;AgentServiceTest 已适配三参构造器)。

- [ ] **Step 8: 跑全部后端测试(回归)**

Run: `cd backend && mvn -q test`
Expected: 所有测试通过(AgentService、AgentRepository[需 Docker]、AgentController、ChatController、FlowiseClient、AgentImportExport、MeController)。

> 若 Docker 未运行,`AgentRepositoryTest` 会失败;启动 Docker 后重跑,或单独跳过该类验证其余:`mvn -q test -Dtest='!AgentRepositoryTest'`。

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/agent/dto/AgentPackage.java backend/src/main/java/com/softmatrix/portal/agent/AgentService.java backend/src/main/java/com/softmatrix/portal/agent/AgentController.java backend/src/test/java/com/softmatrix/portal/agent/AgentImportExportTest.java backend/src/test/java/com/softmatrix/portal/agent/AgentServiceTest.java
git commit -m "backend: add agent import/export with flowise flow definition"
```

---

## Phase 5 — 前端

### Task 11: 前端类型与 API 客户端

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/agents.ts`

- [ ] **Step 1: 扩展 types.ts**

整体替换 `frontend/src/api/types.ts`:

```ts
export interface UserInfo {
  username: string;
  name: string;
}

export type AgentStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED';

export interface Agent {
  id: string;
  name: string;
  description: string | null;
  category: string | null;
  tags: string[];
  flowiseChatflowId: string;
  status: AgentStatus;
  owner: string;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AgentRequest {
  name: string;
  description?: string;
  category?: string;
  tags?: string[];
  flowiseChatflowId?: string;
}

export interface AgentFilters {
  category?: string;
  status?: AgentStatus;
  keyword?: string;
  tag?: string;
}

// 导入导出打包结构
export interface AgentPackage {
  softmatrixVersion: string;
  agent: { name: string; description?: string; category?: string; tags?: string[] };
  flow: { name: string; flowData: unknown };
}
```

- [ ] **Step 2: 扩展 agents.ts**

整体替换 `frontend/src/api/agents.ts`:

```ts
import { client } from './client';
import type { Agent, AgentRequest, AgentFilters, AgentPackage } from './types';

export async function listAgents(filters: AgentFilters = {}): Promise<Agent[]> {
  const { data } = await client.get('/api/agents', { params: filters });
  return data;
}

export async function listCategories(): Promise<string[]> {
  const { data } = await client.get('/api/agents/categories');
  return data;
}

export async function listTags(): Promise<string[]> {
  const { data } = await client.get('/api/agents/tags');
  return data;
}

export async function createAgent(req: AgentRequest): Promise<Agent> {
  const { data } = await client.post('/api/agents', req);
  return data;
}

export async function updateAgent(id: string, req: AgentRequest): Promise<Agent> {
  const { data } = await client.put(`/api/agents/${id}`, req);
  return data;
}

export async function deleteAgent(id: string): Promise<void> {
  await client.delete(`/api/agents/${id}`);
}

export async function publishAgent(id: string): Promise<Agent> {
  const { data } = await client.post(`/api/agents/${id}/publish`);
  return data;
}

export async function disableAgent(id: string): Promise<Agent> {
  const { data } = await client.post(`/api/agents/${id}/disable`);
  return data;
}

export async function withdrawAgent(id: string): Promise<Agent> {
  const { data } = await client.post(`/api/agents/${id}/withdraw`);
  return data;
}

export async function exportAgent(id: string): Promise<AgentPackage> {
  const { data } = await client.get(`/api/agents/${id}/export`);
  return data;
}

export async function importAgent(pkg: AgentPackage): Promise<Agent> {
  const { data } = await client.post('/api/agents/import', pkg);
  return data;
}
```

- [ ] **Step 3: 类型检查**

Run: `cd frontend && npx tsc -b`
Expected: 报错仅限 `AgentListPage.tsx`(仍用旧签名)——下一任务修复。若想先确认本文件无误,可临时忽略;不提交。

> 说明:本任务与 Task 12 一起提交(见 Task 12 Step 3)。

---

### Task 12: AgentListPage — 筛选、状态、动态操作、导入导出

**Files:**
- Modify: `frontend/src/pages/AgentListPage.tsx`

- [ ] **Step 1: 整体替换 AgentListPage.tsx**

```tsx
import { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Table, Button, Modal, Form, Input, Select, AutoComplete, Space, Tag, Popconfirm, message,
} from 'antd';
import type { Agent, AgentRequest, AgentFilters, AgentStatus, AgentPackage } from '../api/types';
import {
  listAgents, createAgent, updateAgent, deleteAgent,
  publishAgent, disableAgent, withdrawAgent,
  listCategories, listTags, exportAgent, importAgent,
} from '../api/agents';

const STATUS_LABEL: Record<AgentStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  PUBLISHED: { text: '已发布', color: 'green' },
  DISABLED: { text: '已停用', color: 'red' },
};

export default function AgentListPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<AgentFilters>({});
  const [categories, setCategories] = useState<string[]>([]);
  const [tags, setTags] = useState<string[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Agent | null>(null);
  const [form] = Form.useForm<AgentRequest>();
  const fileInput = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  const reload = useCallback(() => {
    setLoading(true);
    listAgents(filters).then(setAgents).finally(() => setLoading(false));
  }, [filters]);

  useEffect(() => { reload(); }, [reload]);
  useEffect(() => {
    listCategories().then(setCategories);
    listTags().then(setTags);
  }, [agents.length]);

  const openCreate = () => { setEditing(null); form.resetFields(); setModalOpen(true); };
  const openEdit = (a: Agent) => {
    setEditing(a);
    form.setFieldsValue({
      name: a.name, description: a.description ?? '',
      category: a.category ?? undefined, tags: a.tags,
      flowiseChatflowId: a.flowiseChatflowId,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    try {
      if (editing) await updateAgent(editing.id, values);
      else await createAgent(values);
      message.success('保存成功');
      setModalOpen(false);
      reload();
    } catch (e: any) {
      message.error(e.response?.data?.message ?? '保存失败');
    }
  };

  const runAction = async (fn: () => Promise<unknown>, ok: string) => {
    try { await fn(); message.success(ok); reload(); }
    catch (e: any) { message.error(e.response?.data?.message ?? '操作失败'); }
  };

  const doExport = async (a: Agent) => {
    try {
      const pkg = await exportAgent(a.id);
      const blob = new Blob([JSON.stringify(pkg, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `softmatrix-agent-${a.name}.json`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (e: any) {
      message.error(e.response?.data?.message ?? '导出失败');
    }
  };

  const onImportFile = async (file: File) => {
    try {
      const pkg: AgentPackage = JSON.parse(await file.text());
      await importAgent(pkg);
      message.success('导入成功(草稿)');
      reload();
    } catch (e: any) {
      message.error(e.response?.data?.message ?? '导入失败:文件无效');
    }
  };

  const actionsFor = (a: Agent) => {
    const btns = [] as JSX.Element[];
    if (a.status === 'PUBLISHED') {
      btns.push(<Button key="run" size="small" type="link" onClick={() => navigate(`/agents/${a.id}/chat`)}>运行</Button>);
    }
    btns.push(<Button key="edit" size="small" type="link" onClick={() => openEdit(a)}>编辑</Button>);
    if (a.status === 'DRAFT' || a.status === 'DISABLED') {
      btns.push(<Button key="pub" size="small" type="link" onClick={() => runAction(() => publishAgent(a.id), a.status === 'DISABLED' ? '已重新启用' : '已发布')}>{a.status === 'DISABLED' ? '重新启用' : '发布'}</Button>);
    }
    if (a.status === 'PUBLISHED') {
      btns.push(<Button key="dis" size="small" type="link" onClick={() => runAction(() => disableAgent(a.id), '已停用')}>停用</Button>);
      btns.push(<Button key="wd" size="small" type="link" onClick={() => runAction(() => withdrawAgent(a.id), '已撤回为草稿')}>撤回</Button>);
    }
    btns.push(<Button key="exp" size="small" type="link" onClick={() => doExport(a)}>导出</Button>);
    btns.push(
      <Popconfirm key="del" title="确认删除?" onConfirm={() => runAction(() => deleteAgent(a.id), '已删除')}>
        <Button size="small" type="link" danger>删除</Button>
      </Popconfirm>
    );
    return <Space size={0} wrap>{btns}</Space>;
  };

  return (
    <>
      <Space style={{ marginBottom: 16 }} wrap>
        <Button type="primary" onClick={openCreate}>新建 Agent</Button>
        <Button onClick={() => fileInput.current?.click()}>导入</Button>
        <input ref={fileInput} type="file" accept="application/json" style={{ display: 'none' }}
          onChange={(e) => { const f = e.target.files?.[0]; if (f) onImportFile(f); e.target.value = ''; }} />
        <Input.Search allowClear placeholder="名称关键字" style={{ width: 160 }}
          onSearch={(v) => setFilters((f) => ({ ...f, keyword: v || undefined }))} />
        <Select allowClear placeholder="分类" style={{ width: 120 }}
          options={categories.map((c) => ({ value: c }))}
          onChange={(v) => setFilters((f) => ({ ...f, category: v }))} />
        <Select allowClear placeholder="标签" style={{ width: 120 }}
          options={tags.map((t) => ({ value: t }))}
          onChange={(v) => setFilters((f) => ({ ...f, tag: v }))} />
        <Select allowClear placeholder="状态" style={{ width: 120 }}
          options={[{ value: 'DRAFT', label: '草稿' }, { value: 'PUBLISHED', label: '已发布' }, { value: 'DISABLED', label: '已停用' }]}
          onChange={(v) => setFilters((f) => ({ ...f, status: v as AgentStatus }))} />
      </Space>

      <Table<Agent> rowKey="id" loading={loading} dataSource={agents}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '分类', dataIndex: 'category' },
          { title: '标签', dataIndex: 'tags', render: (t: string[]) => t?.map((x) => <Tag key={x}>{x}</Tag>) },
          { title: '状态', dataIndex: 'status', render: (s: AgentStatus) => <Tag color={STATUS_LABEL[s].color}>{STATUS_LABEL[s].text}</Tag> },
          { title: 'Owner', dataIndex: 'owner' },
          { title: '操作', render: (_, a) => actionsFor(a) },
        ]}
      />

      <Modal title={editing ? '编辑 Agent' : '新建 Agent'} open={modalOpen}
        onOk={submit} onCancel={() => setModalOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, max: 100 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <AutoComplete options={categories.map((c) => ({ value: c }))}
              placeholder="选择或输入分类" filterOption
              allowClear />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Select mode="tags" placeholder="输入标签,回车添加"
              options={tags.map((t) => ({ value: t }))} />
          </Form.Item>
          {!editing && (
            <Form.Item name="flowiseChatflowId" label="Flowise Chatflow ID"
              rules={[{ required: true, max: 64 }]}>
              <Input placeholder="从 Flowise 复制的 Chatflow ID" />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </>
  );
}
```

- [ ] **Step 2: 类型检查**

Run: `cd frontend && npx tsc -b`
Expected: 通过(ChatPage 未改动,仍合法)。

- [ ] **Step 3: Commit(Task 11 + 12)**

```bash
git add frontend/src/api/types.ts frontend/src/api/agents.ts frontend/src/pages/AgentListPage.tsx
git commit -m "frontend: agent management — filters, status, actions, import/export"
```

---

### Task 13: ChatPage — 409 未发布提示

**Files:**
- Modify: `frontend/src/pages/ChatPage.tsx`

- [ ] **Step 1: 处理 409**

`frontend/src/api/chat.ts` 的 `streamChat` 在 `!res.ok` 时统一回调 `onError('运行失败,请重试')`。为区分未发布(409),在 `ChatPage.tsx` 里改由 `chat.ts` 传出状态码——最小改动方案:在 `chat.ts` 的 401 分支后、`!res.ok` 分支前插入 409 处理。

修改 `frontend/src/api/chat.ts`,在:

```ts
  if (res.status === 401) {
    window.location.href = '/oauth2/authorization/keycloak';
    return;
  }
```

之后插入:

```ts
  if (res.status === 409) {
    cb.onError('该 Agent 未发布,无法运行');
    return;
  }
```

- [ ] **Step 2: 类型检查 + 生产构建**

Run: `cd frontend && npm run build`
Expected: 类型检查通过,构建成功。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/chat.ts
git commit -m "frontend: show 'not published' message on 409 in chat"
```

---

## Phase 6 — 端到端联调与验收

### Task 14: 端到端验收

**Files:** 无(联调与验收)

- [ ] **Step 1: 启动依赖并跑迁移**

Run: `cd infra && docker compose up -d`,然后 `cd backend && mvn spring-boot:run`
Expected: Flyway 应用 V2(或基线 V1 后应用 V2);应用启动无异常。存量 Agent 变为 PUBLISHED。

- [ ] **Step 2: 启动前端**

Run(另开终端): `cd frontend && npm run dev`
Expected: `http://localhost:5173` 可访问。

- [ ] **Step 3: 逐条走查验收(对照 spec 第 10 节)**

- [ ] 存量 Agent 迁移后为"已发布"且可运行
- [ ] 新建 Agent 默认"草稿",无"运行"按钮;点"发布"后出现"运行"且可对话
- [ ] "已发布"点"停用"→ 变"已停用",运行入口消失;若直接构造 chat 请求,返回 409(前端提示未发布)
- [ ] "已停用"点"重新启用"→ 回到"已发布"可运行
- [ ] "已发布"点"撤回"→ 回到"草稿"
- [ ] 新建/编辑可设置分类(下拉可输入)与多个标签
- [ ] 顶部按分类、标签、状态、名称关键字筛选生效;分类/标签下拉取自实际数据
- [ ] 对一个 Agent 点"导出"→ 下载 JSON(含 flowData)
- [ ] "导入"上传该 JSON → 列表新增一个"草稿" Agent(Flowise 中新建了流)
- [ ] 导入一个残缺 JSON(删掉 flow 字段)→ 提示导入失败(400)

- [ ] **Step 4: 跑全部后端测试(需 Docker)**

Run: `cd backend && mvn -q test`
Expected: 全部通过。

- [ ] **Step 5: Commit(如有联调修复)**

```bash
git add -A
git commit -m "chore: end-to-end wiring fixes for agent management" || echo "no changes to commit"
```

---

## Self-Review 检查记录

- **Spec 覆盖:** §3 数据模型+迁移→Task 1/2/3;§4 状态机+门控→Task 5(转换)/Task 8(Chat 门控);§5 API(筛选、categories/tags、create/update、publish/disable/withdraw、export/import、chat)→Task 5/6/7/8/10;§6 导入导出+FlowiseClient→Task 9/10;§7 前端→Task 11/12/13;§8 错误处理(409/400 转换/IMPORT_INVALID/502)→Task 8/5/10/9;§9 测试(单测/MockWebServer/WebMvc/Testcontainers)→各 Task;§10 验收→Task 14。无遗漏。
- **占位符:** 通读无 TBD/TODO;每个代码步骤均给出完整代码。
- **类型一致性:** `AgentService` 构造器在 Task 5 为二参,Task 10 改为三参(加 `FlowiseClient`);Task 10 Step 5 显式更新 `AgentServiceTest` 至三参构造,并并入 Task 10 Step 9 提交,避免顺序编译失败。`AgentControllerTest`/`ChatControllerTest` 用 `@MockBean`,不受影响。
- **状态转换命名:** `publish/disable/withdraw` 在 Service(Task 5)、Controller(Task 7)、前端 agents.ts(Task 11)一致;`AgentStatus` 值 DRAFT/PUBLISHED/DISABLED 前后端一致。
