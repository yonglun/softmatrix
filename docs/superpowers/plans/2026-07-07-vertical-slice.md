# EAP 垂直切片实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通 Keycloak 登录 → Portal 看到 Agent → Chat 流式对话(经 Flowise + Azure OpenAI)→ 看到结果 的最窄端到端链路。

**Architecture:** BFF 模式 —— React SPA 只与 Spring Boot 后端通过 HttpOnly Session Cookie 通信;后端用 Spring Security OAuth2 Client 托管 OIDC 登录,并作为唯一出口调用 Flowise Prediction API(流式)与校验 Chatflow。依赖服务(Keycloak / Flowise / PostgreSQL)由本地 docker-compose 提供。

**Tech Stack:** Spring Boot 3.3 (Java 21, Maven)、Spring Security OAuth2 Client、Spring Data JPA、WebClient (reactor)、React 18 + Vite + TypeScript + Ant Design 5、PostgreSQL 16、Keycloak 25、Flowise。

---

## 文件结构

```
softmatrix/
├── infra/
│   ├── docker-compose.yml                 # Keycloak + Flowise + PostgreSQL
│   ├── postgres/init.sql                  # 创建 portal / keycloak / flowise 三库
│   └── keycloak/realm-export.json         # softmatrix realm + portal client + 测试用户
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/softmatrix/portal/
│       │   ├── PortalApplication.java
│       │   ├── config/SecurityConfig.java         # BFF 安全配置
│       │   ├── auth/MeController.java              # GET /api/me
│       │   ├── auth/UserInfo.java                  # record DTO
│       │   ├── agent/AgentEntity.java
│       │   ├── agent/AgentRepository.java
│       │   ├── agent/AgentService.java
│       │   ├── agent/AgentController.java
│       │   ├── agent/dto/AgentRequest.java
│       │   ├── agent/dto/AgentResponse.java
│       │   ├── chat/FlowiseClient.java             # 校验 Chatflow + 流式对话
│       │   ├── chat/ChatController.java            # POST /api/agents/{id}/chat (SSE)
│       │   ├── chat/dto/ChatRequest.java
│       │   └── common/
│       │       ├── ApiException.java
│       │       ├── ErrorResponse.java
│       │       └── GlobalExceptionHandler.java
│       ├── main/resources/application.yml
│       └── test/java/com/softmatrix/portal/
│           ├── agent/AgentServiceTest.java
│           ├── agent/AgentControllerTest.java
│           ├── chat/FlowiseClientTest.java
│           └── auth/MeControllerTest.java
└── frontend/
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── index.html
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── api/client.ts                  # axios 实例 + 401 拦截
        ├── api/agents.ts                  # Agent CRUD 调用
        ├── api/chat.ts                    # fetch + SSE 流式读取
        ├── api/types.ts
        ├── layouts/AppLayout.tsx          # 顶栏(用户名 / 登出)
        ├── pages/AgentListPage.tsx
        └── pages/ChatPage.tsx
```

**约定:**
- 后端 Java 包根:`com.softmatrix.portal`
- 后端端口 `8080`,前端 dev server `5173`,Vite 代理 `/api`、`/oauth2`、`/login`、`/logout` 到后端
- Keycloak `8081`,Flowise `3000`,PostgreSQL `5432`(均由 docker-compose 暴露到宿主机)

---

## Phase 1 — 基础设施(docker-compose)

### Task 1: PostgreSQL 初始化脚本

**Files:**
- Create: `infra/postgres/init.sql`

- [ ] **Step 1: 编写建库脚本**

```sql
-- infra/postgres/init.sql
-- 单实例、三个独立数据库。Keycloak 与 Flowise 需要各自的库。
CREATE DATABASE portal;
CREATE DATABASE keycloak;
CREATE DATABASE flowise;
```

- [ ] **Step 2: Commit**

```bash
git add infra/postgres/init.sql
git commit -m "infra: add postgres init script for three databases"
```

---

### Task 2: Keycloak realm 导出文件

**Files:**
- Create: `infra/keycloak/realm-export.json`

- [ ] **Step 1: 编写 realm 定义**

包含 realm `softmatrix`、机密客户端 `portal`(标准授权码流,重定向到后端回调),以及两个测试用户。密码以明文 value + 非 temporary 方式预置(仅用于本地开发)。

```json
{
  "realm": "softmatrix",
  "enabled": true,
  "sslRequired": "none",
  "registrationAllowed": false,
  "clients": [
    {
      "clientId": "portal",
      "enabled": true,
      "protocol": "openid-connect",
      "publicClient": false,
      "secret": "portal-secret",
      "standardFlowEnabled": true,
      "directAccessGrantsEnabled": false,
      "redirectUris": ["http://localhost:8080/login/oauth2/code/keycloak"],
      "webOrigins": ["http://localhost:8080"],
      "attributes": {
        "post.logout.redirect.uris": "http://localhost:5173/*"
      }
    }
  ],
  "users": [
    {
      "username": "admin",
      "enabled": true,
      "firstName": "Platform",
      "lastName": "Admin",
      "email": "admin@softmatrix.local",
      "emailVerified": true,
      "credentials": [
        { "type": "password", "value": "admin123", "temporary": false }
      ]
    },
    {
      "username": "user",
      "enabled": true,
      "firstName": "Business",
      "lastName": "User",
      "email": "user@softmatrix.local",
      "emailVerified": true,
      "credentials": [
        { "type": "password", "value": "user123", "temporary": false }
      ]
    }
  ]
}
```

- [ ] **Step 2: Commit**

```bash
git add infra/keycloak/realm-export.json
git commit -m "infra: add keycloak softmatrix realm export with test users"
```

---

### Task 3: docker-compose 编排

**Files:**
- Create: `infra/docker-compose.yml`

- [ ] **Step 1: 编写编排文件**

```yaml
# infra/docker-compose.yml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - ./postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 10

  keycloak:
    image: quay.io/keycloak/keycloak:25.0
    command: ["start-dev", "--import-realm"]
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: postgres
      KC_DB_PASSWORD: postgres
      KC_HTTP_PORT: 8081
    ports:
      - "8081:8081"
    volumes:
      - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro
    depends_on:
      postgres:
        condition: service_healthy

  flowise:
    image: flowiseai/flowise:latest
    environment:
      DATABASE_TYPE: postgres
      DATABASE_HOST: postgres
      DATABASE_PORT: 5432
      DATABASE_NAME: flowise
      DATABASE_USER: postgres
      DATABASE_PASSWORD: postgres
      FLOWISE_USERNAME: admin
      FLOWISE_PASSWORD: admin
      # API Key,Portal 后端用它调用 Prediction API
      FLOWISE_API_KEY: portal-flowise-key
      PORT: 3000
    ports:
      - "3000:3000"
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  pgdata:
```

- [ ] **Step 2: 启动并验证依赖服务**

Run:
```bash
cd infra && docker compose up -d && docker compose ps
```
Expected: `postgres`、`keycloak`、`flowise` 三个容器均为 `running`(Keycloak 首次启动导入 realm 需约 30-60s)。

- [ ] **Step 3: 验证 Keycloak realm 已导入**

Run:
```bash
curl -s http://localhost:8081/realms/softmatrix/.well-known/openid-configuration | head -c 200
```
Expected: 返回包含 `"issuer":"http://localhost:8081/realms/softmatrix"` 的 JSON(非 404)。

- [ ] **Step 4: 验证 Flowise 可达**

Run:
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/api/v1/ping
```
Expected: `200`。

- [ ] **Step 5: Commit**

```bash
git add infra/docker-compose.yml
git commit -m "infra: add docker-compose for keycloak, flowise, postgres"
```

- [ ] **Step 6: 人工准备演示 Chatflow(一次性,非代码)**

打开 `http://localhost:3000`(admin/admin),新建一个 Chatflow:`Azure ChatOpenAI` 节点 → `Conversation Chain`(带 Buffer Memory)。填入 Azure OpenAI 的 endpoint / deployment / apiVersion / apiKey。保存后从 URL 或 Settings 复制该 Chatflow 的 **ID**,记下备用(Agent 登记时填入)。此步骤产出的是一个可用的 Chatflow ID,不入库到本仓库。

---

## Phase 2 — 后端骨架与安全(BFF)

### Task 4: Maven 项目与 Spring Boot 骨架

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/softmatrix/portal/PortalApplication.java`
- Create: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 编写 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.2</version>
    <relativePath/>
  </parent>

  <groupId>com.softmatrix</groupId>
  <artifactId>portal</artifactId>
  <version>0.1.0</version>
  <name>portal</name>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.squareup.okhttp3</groupId>
      <artifactId>mockwebserver</artifactId>
      <version>4.12.0</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: 编写主类**

```java
// backend/src/main/java/com/softmatrix/portal/PortalApplication.java
package com.softmatrix.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PortalApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}
```

- [ ] **Step 3: 编写 application.yml**

```yaml
# backend/src/main/resources/application.yml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/portal
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: portal
            client-secret: portal-secret
            authorization-grant-type: authorization_code
            scope: openid,profile,email
        provider:
          keycloak:
            issuer-uri: http://localhost:8081/realms/softmatrix

flowise:
  base-url: http://localhost:3000
  api-key: portal-flowise-key
```

- [ ] **Step 4: 验证项目可编译**

Run:
```bash
cd backend && ./mvnw -q -DskipTests compile || mvn -q -DskipTests compile
```
Expected: 编译成功,无错误(首次会下载依赖)。

> 注:若 `mvnw` 不存在,先运行 `cd backend && mvn -N wrapper:wrapper` 生成 wrapper,或直接使用本机 `mvn`。

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/softmatrix/portal/PortalApplication.java backend/src/main/resources/application.yml
git commit -m "backend: scaffold spring boot project"
```

---

### Task 5: 通用错误处理

**Files:**
- Create: `backend/src/main/java/com/softmatrix/portal/common/ErrorResponse.java`
- Create: `backend/src/main/java/com/softmatrix/portal/common/ApiException.java`
- Create: `backend/src/main/java/com/softmatrix/portal/common/GlobalExceptionHandler.java`

- [ ] **Step 1: 编写统一错误响应 DTO**

```java
// common/ErrorResponse.java
package com.softmatrix.portal.common;

public record ErrorResponse(String code, String message) {}
```

- [ ] **Step 2: 编写业务异常**

```java
// common/ApiException.java
package com.softmatrix.portal.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
```

- [ ] **Step 3: 编写全局异常处理器**

```java
// common/GlobalExceptionHandler.java
package com.softmatrix.portal.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", msg));
    }
}
```

- [ ] **Step 4: 验证编译**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: 成功。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/common/
git commit -m "backend: add unified error handling"
```

---

### Task 6: BFF 安全配置

**Files:**
- Create: `backend/src/main/java/com/softmatrix/portal/config/SecurityConfig.java`

- [ ] **Step 1: 编写安全配置**

关键点:`/api/**` 需认证且未认证返回 401(不重定向);其余(OAuth2 端点)走标准登录流;登出后重定向回前端。

```java
// config/SecurityConfig.java
package com.softmatrix.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth -> oauth
                .defaultSuccessUrl("http://localhost:5173/", true)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("http://localhost:5173/")
            )
            // 对 /api/** 未认证返回 401,而不是 302 重定向到 Keycloak
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/**"))
            )
            // SPA 通过 POST /logout 触发登出;此处禁用 CSRF 以简化切片(后续子项目加固)
            .csrf(csrf -> csrf.disable());
        return http;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: 成功。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/config/SecurityConfig.java
git commit -m "backend: add BFF security config with 401 for /api"
```

---

### Task 7: `/api/me` 端点

**Files:**
- Create: `backend/src/main/java/com/softmatrix/portal/auth/UserInfo.java`
- Create: `backend/src/main/java/com/softmatrix/portal/auth/MeController.java`
- Test: `backend/src/test/java/com/softmatrix/portal/auth/MeControllerTest.java`

- [ ] **Step 1: 编写失败测试**

验证未认证返回 401,认证后返回用户名。

```java
// test/.../auth/MeControllerTest.java
package com.softmatrix.portal.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeController.class)
@Import(com.softmatrix.portal.config.SecurityConfig.class)
class MeControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void unauthenticated_returns_401() throws Exception {
        mvc.perform(get("/api/me"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticated_returns_username() throws Exception {
        mvc.perform(get("/api/me").with(oidcLogin()
                .idToken(t -> t.claim("preferred_username", "admin")
                               .claim("name", "Platform Admin"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.username").value("admin"))
           .andExpect(jsonPath("$.name").value("Platform Admin"));
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `cd backend && mvn -q test -Dtest=MeControllerTest`
Expected: 编译失败或测试失败(`MeController` / `UserInfo` 尚不存在)。

- [ ] **Step 3: 编写 UserInfo 与 MeController**

```java
// auth/UserInfo.java
package com.softmatrix.portal.auth;

public record UserInfo(String username, String name) {}
```

```java
// auth/MeController.java
package com.softmatrix.portal.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    @GetMapping("/api/me")
    public UserInfo me(@AuthenticationPrincipal OidcUser principal) {
        return new UserInfo(
                principal.getPreferredUsername(),
                principal.getFullName());
    }
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `cd backend && mvn -q test -Dtest=MeControllerTest`
Expected: PASS(2 个测试通过)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/auth/ backend/src/test/java/com/softmatrix/portal/auth/
git commit -m "backend: add /api/me endpoint"
```

---

## Phase 3 — Agent CRUD

### Task 8: Agent 实体与仓库

**Files:**
- Create: `backend/src/main/java/com/softmatrix/portal/agent/AgentEntity.java`
- Create: `backend/src/main/java/com/softmatrix/portal/agent/AgentRepository.java`

- [ ] **Step 1: 编写实体**

```java
// agent/AgentEntity.java
package com.softmatrix.portal.agent;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent")
public class AgentEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "flowise_chatflow_id", nullable = false, length = 64)
    private String flowiseChatflowId;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // getters / setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFlowiseChatflowId() { return flowiseChatflowId; }
    public void setFlowiseChatflowId(String v) { this.flowiseChatflowId = v; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 2: 编写仓库**

```java
// agent/AgentRepository.java
package com.softmatrix.portal.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<AgentEntity, UUID> {
}
```

- [ ] **Step 3: 验证编译**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: 成功。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/agent/AgentEntity.java backend/src/main/java/com/softmatrix/portal/agent/AgentRepository.java
git commit -m "backend: add agent entity and repository"
```

---

### Task 9: Agent DTO 与 Service(含 Chatflow 校验挂钩)

**Files:**
- Create: `backend/src/main/java/com/softmatrix/portal/agent/dto/AgentRequest.java`
- Create: `backend/src/main/java/com/softmatrix/portal/agent/dto/AgentResponse.java`
- Create: `backend/src/main/java/com/softmatrix/portal/agent/AgentService.java`
- Test: `backend/src/test/java/com/softmatrix/portal/agent/AgentServiceTest.java`

> 说明:`AgentService.create` 在保存前需校验 Chatflow 是否存在,依赖 `FlowiseClient`(Task 11 定义接口方法 `chatflowExists`)。为让本任务可独立测试,这里将 `FlowiseClient` 作为构造依赖注入并在测试中用 Mockito 打桩。`FlowiseClient` 的真实实现放在 Task 11;本任务先只用到它的一个方法签名 `boolean chatflowExists(String id)`。为避免顺序耦合,**在本任务先创建 `FlowiseClient` 的最小接口占位**:一个仅含 `chatflowExists` 抽象方法的类由 Task 11 完整实现。这里改为定义一个接口。

- [ ] **Step 1: 编写 DTO**

```java
// agent/dto/AgentRequest.java
package com.softmatrix.portal.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        @NotBlank @Size(max = 64) String flowiseChatflowId
) {}
```

```java
// agent/dto/AgentResponse.java
package com.softmatrix.portal.agent.dto;

import com.softmatrix.portal.agent.AgentEntity;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentResponse(
        UUID id,
        String name,
        String description,
        String flowiseChatflowId,
        String owner,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AgentResponse from(AgentEntity e) {
        return new AgentResponse(e.getId(), e.getName(), e.getDescription(),
                e.getFlowiseChatflowId(), e.getOwner(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
```

- [ ] **Step 2: 定义 Chatflow 校验接口(供 Service 依赖)**

```java
// chat/ChatflowValidator.java
package com.softmatrix.portal.chat;

public interface ChatflowValidator {
    /** Chatflow 是否存在于 Flowise。 */
    boolean chatflowExists(String chatflowId);
}
```

- [ ] **Step 3: 编写失败测试**

```java
// test/.../agent/AgentServiceTest.java
package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentRequest;
import com.softmatrix.portal.agent.dto.AgentResponse;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void create_rejects_invalid_chatflow() {
        when(validator.chatflowExists("bad")).thenReturn(false);
        AgentRequest req = new AgentRequest("A", "d", "bad");

        assertThatThrownBy(() -> service.create(req, "admin"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Chatflow");
        verify(repo, never()).save(any());
    }

    @Test
    void create_saves_when_chatflow_valid() {
        when(validator.chatflowExists("good")).thenReturn(true);
        AgentRequest req = new AgentRequest("A", "d", "good");

        AgentResponse res = service.create(req, "admin");

        assertThat(res.name()).isEqualTo("A");
        assertThat(res.owner()).isEqualTo("admin");
        verify(repo).save(any(AgentEntity.class));
    }
}
```

- [ ] **Step 4: 运行测试,确认失败**

Run: `cd backend && mvn -q test -Dtest=AgentServiceTest`
Expected: 编译失败(`AgentService` 不存在)。

- [ ] **Step 5: 编写 Service**

```java
// agent/AgentService.java
package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentRequest;
import com.softmatrix.portal.agent.dto.AgentResponse;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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

    public List<AgentResponse> list() {
        return repo.findAll().stream().map(AgentResponse::from).toList();
    }

    public AgentResponse create(AgentRequest req, String owner) {
        requireChatflow(req.flowiseChatflowId());
        AgentEntity e = new AgentEntity();
        e.setName(req.name());
        e.setDescription(req.description());
        e.setFlowiseChatflowId(req.flowiseChatflowId());
        e.setOwner(owner);
        return AgentResponse.from(repo.save(e));
    }

    public AgentResponse update(UUID id, AgentRequest req) {
        AgentEntity e = find(id);
        requireChatflow(req.flowiseChatflowId());
        e.setName(req.name());
        e.setDescription(req.description());
        e.setFlowiseChatflowId(req.flowiseChatflowId());
        return AgentResponse.from(repo.save(e));
    }

    public void delete(UUID id) {
        repo.delete(find(id));
    }

    public AgentEntity find(UUID id) {
        return repo.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent 不存在"));
    }

    private void requireChatflow(String chatflowId) {
        if (!validator.chatflowExists(chatflowId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHATFLOW",
                    "指定的 Chatflow 不存在,请检查 Chatflow ID");
        }
    }
}
```

- [ ] **Step 6: 运行测试,确认通过**

Run: `cd backend && mvn -q test -Dtest=AgentServiceTest`
Expected: PASS(2 个测试)。

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/agent/ backend/src/main/java/com/softmatrix/portal/chat/ChatflowValidator.java backend/src/test/java/com/softmatrix/portal/agent/AgentServiceTest.java
git commit -m "backend: add agent service with chatflow validation"
```

---

### Task 10: Agent Controller

**Files:**
- Create: `backend/src/main/java/com/softmatrix/portal/agent/AgentController.java`
- Test: `backend/src/test/java/com/softmatrix/portal/agent/AgentControllerTest.java`

- [ ] **Step 1: 编写失败测试**

```java
// test/.../agent/AgentControllerTest.java
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

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
@Import(SecurityConfig.class)
class AgentControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AgentService service;
    @MockBean ChatflowValidator validator; // SecurityConfig 无需,但避免上下文缺 bean

    @Test
    void list_requires_auth() throws Exception {
        mvc.perform(get("/api/agents"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void list_returns_agents_when_authenticated() throws Exception {
        when(service.list()).thenReturn(List.of(new AgentResponse(
                UUID.randomUUID(), "A", "d", "cf1", "admin",
                OffsetDateTime.now(), OffsetDateTime.now())));

        mvc.perform(get("/api/agents").with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("A"));
    }

    @Test
    void create_uses_username_as_owner() throws Exception {
        when(service.create(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("admin")))
            .thenReturn(new AgentResponse(UUID.randomUUID(), "A", "d", "cf1", "admin",
                    OffsetDateTime.now(), OffsetDateTime.now()));

        mvc.perform(post("/api/agents")
                .with(oidcLogin().idToken(t -> t.claim("preferred_username", "admin")))
                .contentType("application/json")
                .content("{\"name\":\"A\",\"description\":\"d\",\"flowiseChatflowId\":\"cf1\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.owner").value("admin"));
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `cd backend && mvn -q test -Dtest=AgentControllerTest`
Expected: 编译失败(`AgentController` 不存在)。

- [ ] **Step 3: 编写 Controller**

```java
// agent/AgentController.java
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
    public List<AgentResponse> list() {
        return service.list();
    }

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
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `cd backend && mvn -q test -Dtest=AgentControllerTest`
Expected: PASS(3 个测试)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/agent/AgentController.java backend/src/test/java/com/softmatrix/portal/agent/AgentControllerTest.java
git commit -m "backend: add agent CRUD controller"
```

---

## Phase 4 — Flowise 集成与 Chat

### Task 11: FlowiseClient(实现 ChatflowValidator + 流式对话)

**Files:**
- Create: `backend/src/main/java/com/softmatrix/portal/chat/FlowiseClient.java`
- Test: `backend/src/test/java/com/softmatrix/portal/chat/FlowiseClientTest.java`

- [ ] **Step 1: 编写失败测试(用 MockWebServer)**

```java
// test/.../chat/FlowiseClientTest.java
package com.softmatrix.portal.chat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class FlowiseClientTest {

    MockWebServer server;
    FlowiseClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        WebClient wc = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        client = new FlowiseClient(wc, "test-key");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void chatflowExists_true_on_200() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"id\":\"cf1\"}")
                .addHeader("Content-Type", "application/json"));

        assertThat(client.chatflowExists("cf1")).isTrue();
    }

    @Test
    void chatflowExists_false_on_404() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(client.chatflowExists("missing")).isFalse();
    }

    @Test
    void streamPrediction_emits_tokens() {
        // Flowise streaming 以 SSE 返回,每个 token 一行 data:
        String sse = "message:\ndata:{\"event\":\"token\",\"data\":\"Hello\"}\n\n"
                   + "message:\ndata:{\"event\":\"token\",\"data\":\" world\"}\n\n";
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody(sse)
                .addHeader("Content-Type", "text/event-stream"));

        StepVerifier.create(client.streamPrediction("cf1", "sess1", "hi"))
                .expectNext("Hello")
                .expectNext(" world")
                .verifyComplete();
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `cd backend && mvn -q test -Dtest=FlowiseClientTest`
Expected: 编译失败(`FlowiseClient` 不存在)。

- [ ] **Step 3: 编写 FlowiseClient**

`chatflowExists` 用阻塞调用(供 Service 同步校验);`streamPrediction` 返回 `Flux<String>`(逐 token 文本)。解析 Flowise SSE:每个事件的 `data:` 行是 JSON,取 `event=="token"` 的 `data` 字段文本。

```java
// chat/FlowiseClient.java
package com.softmatrix.portal.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

@Component
public class FlowiseClient implements ChatflowValidator {

    private final WebClient webClient;
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public FlowiseClient(WebClient flowiseWebClient,
                         @Value("${flowise.api-key}") String apiKey) {
        this.webClient = flowiseWebClient;
        this.apiKey = apiKey;
    }

    @Override
    public boolean chatflowExists(String chatflowId) {
        try {
            webClient.get()
                    .uri("/api/v1/chatflows/{id}", chatflowId)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 调用 Flowise streaming prediction,逐 token 发出文本片段。 */
    public Flux<String> streamPrediction(String chatflowId, String sessionId, String question) {
        Map<String, Object> body = Map.of(
                "question", question,
                "streaming", true,
                "chatId", sessionId);

        return webClient.post()
                .uri("/api/v1/prediction/{id}", chatflowId)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)   // 每个 SSE data: 行作为一个元素
                .map(this::extractToken)
                .filter(s -> !s.isEmpty());
    }

    private String extractToken(String dataLine) {
        try {
            JsonNode node = mapper.readTree(dataLine);
            if ("token".equals(node.path("event").asText())) {
                return node.path("data").asText("");
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
}
```

- [ ] **Step 4: 提供 WebClient bean**

在 `config/` 下新增 `FlowiseConfig`,以 `flowise.base-url` 构造名为 `flowiseWebClient` 的 bean。

```java
// config/FlowiseConfig.java
package com.softmatrix.portal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class FlowiseConfig {

    @Bean
    public WebClient flowiseWebClient(@Value("${flowise.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
```

- [ ] **Step 5: 运行测试,确认通过**

Run: `cd backend && mvn -q test -Dtest=FlowiseClientTest`
Expected: PASS(3 个测试)。

> 若 `bodyToFlux(String.class)` 对 SSE 的分帧与测试桩不一致导致 `streamPrediction_emits_tokens` 失败,改用 `.bodyToFlux(org.springframework.http.codec.ServerSentEvent.class)` 并在 `extractToken` 中取 `event.data()`。两种写法择一使其通过,不要留空。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/chat/FlowiseClient.java backend/src/main/java/com/softmatrix/portal/config/FlowiseConfig.java backend/src/test/java/com/softmatrix/portal/chat/FlowiseClientTest.java
git commit -m "backend: add flowise client for validation and streaming"
```

---

### Task 12: Chat Controller(SSE 转发)

**Files:**
- Create: `backend/src/main/java/com/softmatrix/portal/chat/dto/ChatRequest.java`
- Create: `backend/src/main/java/com/softmatrix/portal/chat/ChatController.java`
- Test: `backend/src/test/java/com/softmatrix/portal/chat/ChatControllerTest.java`

- [ ] **Step 1: 编写请求 DTO**

```java
// chat/dto/ChatRequest.java
package com.softmatrix.portal.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String sessionId,
        @NotBlank String message
) {}
```

- [ ] **Step 2: 编写失败测试**

验证:未认证 401;认证后返回 `text/event-stream` 且转发 token。控制器查 `AgentService.find` 得 chatflowId 再调 `FlowiseClient.streamPrediction`。

```java
// test/.../chat/ChatControllerTest.java
package com.softmatrix.portal.chat;

import com.softmatrix.portal.agent.AgentEntity;
import com.softmatrix.portal.agent.AgentService;
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
@Import(SecurityConfig.class)
class ChatControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AgentService agentService;
    @MockBean FlowiseClient flowiseClient;

    @Test
    void chat_requires_auth() throws Exception {
        mvc.perform(post("/api/agents/{id}/chat", UUID.randomUUID())
                .contentType("application/json")
                .content("{\"sessionId\":\"s1\",\"message\":\"hi\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void chat_streams_tokens() throws Exception {
        UUID id = UUID.randomUUID();
        AgentEntity e = new AgentEntity();
        e.setFlowiseChatflowId("cf1");
        when(agentService.find(id)).thenReturn(e);
        when(flowiseClient.streamPrediction(eq("cf1"), eq("s1"), eq("hi")))
                .thenReturn(Flux.just("Hello", " world"));

        mvc.perform(post("/api/agents/{id}/chat", id)
                .with(oidcLogin())
                .contentType("application/json")
                .content("{\"sessionId\":\"s1\",\"message\":\"hi\"}"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
    }
}
```

- [ ] **Step 3: 运行测试,确认失败**

Run: `cd backend && mvn -q test -Dtest=ChatControllerTest`
Expected: 编译失败(`ChatController` 不存在)。

- [ ] **Step 4: 编写 Controller**

返回 `Flux<String>` 并声明 `produces = text/event-stream`,Spring MVC 会以 SSE 流式输出。Flowise 出错时映射为 `ApiException(502)`。

```java
// chat/ChatController.java
package com.softmatrix.portal.chat;

import com.softmatrix.portal.agent.AgentEntity;
import com.softmatrix.portal.agent.AgentService;
import com.softmatrix.portal.chat.dto.ChatRequest;
import com.softmatrix.portal.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
public class ChatController {

    private final AgentService agentService;
    private final FlowiseClient flowiseClient;

    public ChatController(AgentService agentService, FlowiseClient flowiseClient) {
        this.agentService = agentService;
        this.flowiseClient = flowiseClient;
    }

    @PostMapping(value = "/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@PathVariable UUID id, @Valid @RequestBody ChatRequest req) {
        AgentEntity agent = agentService.find(id);
        return flowiseClient.streamPrediction(
                        agent.getFlowiseChatflowId(), req.sessionId(), req.message())
                .onErrorMap(ex -> new ApiException(HttpStatus.BAD_GATEWAY,
                        "FLOWISE_ERROR", "运行失败,请重试"));
    }
}
```

- [ ] **Step 5: 运行测试,确认通过**

Run: `cd backend && mvn -q test -Dtest=ChatControllerTest`
Expected: PASS(2 个测试)。

- [ ] **Step 6: 运行全部后端测试**

Run: `cd backend && mvn -q test`
Expected: 所有测试通过(MeController / AgentService / AgentController / FlowiseClient / ChatController)。

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/softmatrix/portal/chat/ backend/src/test/java/com/softmatrix/portal/chat/ChatControllerTest.java
git commit -m "backend: add chat controller with SSE streaming"
```

---

## Phase 5 — 前端

### Task 13: 前端脚手架(Vite + React + TS + AntD)

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/index.html`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`

- [ ] **Step 1: 编写 package.json**

```json
{
  "name": "softmatrix-portal-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "antd": "^5.19.0",
    "axios": "^1.7.0",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.24.0"
  },
  "devDependencies": {
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@vitejs/plugin-react": "^4.3.1",
    "typescript": "^5.5.3",
    "vite": "^5.3.3"
  }
}
```

- [ ] **Step 2: 编写 tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true
  },
  "include": ["src"]
}
```

- [ ] **Step 3: 编写 vite.config.ts(代理后端)**

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
      '/login': { target: 'http://localhost:8080', changeOrigin: true },
      '/logout': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
```

- [ ] **Step 4: 编写 index.html 与入口**

```html
<!-- frontend/index.html -->
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Softmatrix EAP</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

```tsx
// frontend/src/main.tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import 'antd/dist/reset.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
```

```tsx
// frontend/src/App.tsx (占位,Task 16 补路由)
export default function App() {
  return <div>Softmatrix EAP</div>;
}
```

- [ ] **Step 5: 安装依赖并验证构建**

Run:
```bash
cd frontend && npm install && npm run build
```
Expected: 构建成功,生成 `dist/`。

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/tsconfig.json frontend/vite.config.ts frontend/index.html frontend/src/main.tsx frontend/src/App.tsx
git commit -m "frontend: scaffold vite react ts antd project"
```

---

### Task 14: API 客户端(axios + 401 拦截 + 类型)

**Files:**
- Create: `frontend/src/api/types.ts`
- Create: `frontend/src/api/client.ts`
- Create: `frontend/src/api/agents.ts`

- [ ] **Step 1: 编写类型**

```ts
// frontend/src/api/types.ts
export interface UserInfo {
  username: string;
  name: string;
}

export interface Agent {
  id: string;
  name: string;
  description: string | null;
  flowiseChatflowId: string;
  owner: string;
  createdAt: string;
  updatedAt: string;
}

export interface AgentRequest {
  name: string;
  description?: string;
  flowiseChatflowId: string;
}
```

- [ ] **Step 2: 编写 axios 实例与 401 拦截**

401 时把浏览器导航到后端登录发起端点(BFF 会 302 到 Keycloak)。

```ts
// frontend/src/api/client.ts
import axios from 'axios';

export const client = axios.create({ baseURL: '/' });

client.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      window.location.href = '/oauth2/authorization/keycloak';
      return new Promise(() => {}); // 阻断后续处理,页面即将跳转
    }
    return Promise.reject(err);
  }
);

export async function fetchMe() {
  const { data } = await client.get('/api/me');
  return data;
}

export function login() {
  window.location.href = '/oauth2/authorization/keycloak';
}

export function logout() {
  // 后端 /logout 由 Spring Security 处理,登出后重定向回前端
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = '/logout';
  document.body.appendChild(form);
  form.submit();
}
```

- [ ] **Step 3: 编写 Agent API**

```ts
// frontend/src/api/agents.ts
import { client } from './client';
import type { Agent, AgentRequest } from './types';

export async function listAgents(): Promise<Agent[]> {
  const { data } = await client.get('/api/agents');
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
```

- [ ] **Step 4: 验证类型检查**

Run: `cd frontend && npx tsc -b`
Expected: 无类型错误。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/types.ts frontend/src/api/client.ts frontend/src/api/agents.ts
git commit -m "frontend: add api client with 401 interceptor"
```

---

### Task 15: Chat 流式读取客户端

**Files:**
- Create: `frontend/src/api/chat.ts`

- [ ] **Step 1: 编写 SSE fetch 读取器**

因 Chat 是 POST,不能用 `EventSource`;用 `fetch` + `ReadableStream` 手动解析 SSE 帧,逐 token 回调。

```ts
// frontend/src/api/chat.ts
export interface ChatCallbacks {
  onToken: (text: string) => void;
  onError: (message: string) => void;
  onDone: () => void;
}

export async function streamChat(
  agentId: string,
  sessionId: string,
  message: string,
  cb: ChatCallbacks
): Promise<void> {
  let res: Response;
  try {
    res = await fetch(`/api/agents/${agentId}/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ sessionId, message }),
    });
  } catch {
    cb.onError('网络错误,请重试');
    return;
  }

  if (res.status === 401) {
    window.location.href = '/oauth2/authorization/keycloak';
    return;
  }
  if (!res.ok || !res.body) {
    cb.onError('运行失败,请重试');
    return;
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    // SSE 帧以空行分隔;取每帧的 data: 内容
    const frames = buffer.split('\n\n');
    buffer = frames.pop() ?? '';
    for (const frame of frames) {
      for (const line of frame.split('\n')) {
        if (line.startsWith('data:')) {
          cb.onToken(line.slice(5).trimStart());
        }
      }
    }
  }
  cb.onDone();
}
```

- [ ] **Step 2: 验证类型检查**

Run: `cd frontend && npx tsc -b`
Expected: 无类型错误。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/chat.ts
git commit -m "frontend: add streaming chat client"
```

---

### Task 16: 布局、路由与登录态

**Files:**
- Create: `frontend/src/layouts/AppLayout.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: 编写布局(顶栏显示用户名与登出)**

```tsx
// frontend/src/layouts/AppLayout.tsx
import { Layout, Typography, Button, Space } from 'antd';
import type { ReactNode } from 'react';
import type { UserInfo } from '../api/types';
import { logout } from '../api/client';

const { Header, Content } = Layout;

export default function AppLayout({ user, children }: { user: UserInfo; children: ReactNode }) {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ color: '#fff', margin: 0 }}>
          Softmatrix EAP
        </Typography.Title>
        <Space>
          <span style={{ color: '#fff' }}>{user.name || user.username}</span>
          <Button size="small" onClick={logout}>登出</Button>
        </Space>
      </Header>
      <Content style={{ padding: 24 }}>{children}</Content>
    </Layout>
  );
}
```

- [ ] **Step 2: 编写 App(启动拉 /api/me,未登录跳登录;路由)**

```tsx
// frontend/src/App.tsx
import { useEffect, useState } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Spin } from 'antd';
import { fetchMe } from './api/client';
import type { UserInfo } from './api/types';
import AppLayout from './layouts/AppLayout';
import AgentListPage from './pages/AgentListPage';
import ChatPage from './pages/ChatPage';

export default function App() {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchMe()
      .then(setUser)
      .catch(() => { /* 401 拦截器已跳转登录 */ })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Spin style={{ marginTop: '20vh', display: 'block' }} />;
  if (!user) return null; // 正在跳转登录

  return (
    <AppLayout user={user}>
      <Routes>
        <Route path="/" element={<AgentListPage />} />
        <Route path="/agents/:id/chat" element={<ChatPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppLayout>
  );
}
```

- [ ] **Step 3: 验证类型检查(此时 pages 尚未创建,预期报错)**

Run: `cd frontend && npx tsc -b`
Expected: 报 `Cannot find module './pages/AgentListPage'` 等 —— 下一任务创建后消除。此步不提交。

---

### Task 17: Agent 列表页与登记表单

**Files:**
- Create: `frontend/src/pages/AgentListPage.tsx`

- [ ] **Step 1: 编写列表页(表格 + 新建/编辑 Modal + 删除 + 进入 Chat)**

```tsx
// frontend/src/pages/AgentListPage.tsx
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Button, Modal, Form, Input, Space, Popconfirm, message } from 'antd';
import type { Agent, AgentRequest } from '../api/types';
import { listAgents, createAgent, updateAgent, deleteAgent } from '../api/agents';

export default function AgentListPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Agent | null>(null);
  const [form] = Form.useForm<AgentRequest>();
  const navigate = useNavigate();

  const reload = () => {
    setLoading(true);
    listAgents().then(setAgents).finally(() => setLoading(false));
  };

  useEffect(reload, []);

  const openCreate = () => { setEditing(null); form.resetFields(); setModalOpen(true); };
  const openEdit = (a: Agent) => {
    setEditing(a);
    form.setFieldsValue({ name: a.name, description: a.description ?? '', flowiseChatflowId: a.flowiseChatflowId });
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

  const remove = async (id: string) => {
    await deleteAgent(id);
    message.success('已删除');
    reload();
  };

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={openCreate}>新建 Agent</Button>
      </Space>
      <Table<Agent> rowKey="id" loading={loading} dataSource={agents}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '描述', dataIndex: 'description' },
          { title: 'Chatflow ID', dataIndex: 'flowiseChatflowId' },
          { title: 'Owner', dataIndex: 'owner' },
          {
            title: '操作',
            render: (_, a) => (
              <Space>
                <Button size="small" type="link" onClick={() => navigate(`/agents/${a.id}/chat`)}>运行</Button>
                <Button size="small" type="link" onClick={() => openEdit(a)}>编辑</Button>
                <Popconfirm title="确认删除?" onConfirm={() => remove(a.id)}>
                  <Button size="small" type="link" danger>删除</Button>
                </Popconfirm>
              </Space>
            ),
          },
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
          <Form.Item name="flowiseChatflowId" label="Flowise Chatflow ID"
            rules={[{ required: true, max: 64 }]}>
            <Input placeholder="从 Flowise 复制的 Chatflow ID" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
```

- [ ] **Step 2: 验证类型检查(ChatPage 仍缺,预期报错)**

Run: `cd frontend && npx tsc -b`
Expected: 仅剩 `Cannot find module './pages/ChatPage'`。此步不提交。

---

### Task 18: Chat 对话页

**Files:**
- Create: `frontend/src/pages/ChatPage.tsx`

- [ ] **Step 1: 编写对话页(流式渲染 + 错误标记 + 会话上下文)**

`sessionId` 打开页面时生成一次(UUID);多轮对话复用,刷新页面即新会话。

```tsx
// frontend/src/pages/ChatPage.tsx
import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Input, Button, List, Typography, Space } from 'antd';
import { streamChat } from '../api/chat';

interface Msg { role: 'user' | 'assistant'; text: string; error?: boolean; }

export default function ChatPage() {
  const { id } = useParams<{ id: string }>();
  const sessionId = useMemo(() => crypto.randomUUID(), []);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);

  const send = async () => {
    if (!input.trim() || !id) return;
    const question = input;
    setInput('');
    setMessages((m) => [...m, { role: 'user', text: question }, { role: 'assistant', text: '' }]);
    setSending(true);

    const appendToAssistant = (fn: (prev: Msg) => Msg) =>
      setMessages((m) => {
        const copy = [...m];
        copy[copy.length - 1] = fn(copy[copy.length - 1]);
        return copy;
      });

    await streamChat(id, sessionId, question, {
      onToken: (t) => appendToAssistant((prev) => ({ ...prev, text: prev.text + t })),
      onError: (msg) => appendToAssistant((prev) => ({ ...prev, text: prev.text + `\n[${msg}]`, error: true })),
      onDone: () => setSending(false),
    });
    setSending(false);
  };

  return (
    <div style={{ maxWidth: 720, margin: '0 auto' }}>
      <List
        dataSource={messages}
        renderItem={(m) => (
          <List.Item>
            <Typography.Text type={m.error ? 'danger' : m.role === 'user' ? 'secondary' : undefined}>
              <b>{m.role === 'user' ? '我' : 'Agent'}:</b> {m.text}
            </Typography.Text>
          </List.Item>
        )}
      />
      <Space.Compact style={{ width: '100%', marginTop: 16 }}>
        <Input value={input} onChange={(e) => setInput(e.target.value)}
          onPressEnter={send} disabled={sending} placeholder="输入消息..." />
        <Button type="primary" onClick={send} loading={sending}>发送</Button>
      </Space.Compact>
    </div>
  );
}
```

- [ ] **Step 2: 验证全量类型检查与构建**

Run: `cd frontend && npm run build`
Expected: 类型检查通过,构建成功。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/ frontend/src/layouts/ frontend/src/App.tsx
git commit -m "frontend: add layout, agent list and chat pages"
```

---

## Phase 6 — 端到端联调与验收

### Task 19: 端到端手工验收

**Files:** 无(联调与验收)

- [ ] **Step 1: 启动依赖服务**

Run: `cd infra && docker compose up -d && docker compose ps`
Expected: 三容器 running。

- [ ] **Step 2: 启动后端**

Run: `cd backend && mvn spring-boot:run`
Expected: 应用启动于 8080,JPA 自动建 `agent` 表,无异常。

- [ ] **Step 3: 启动前端**

Run(另开终端): `cd frontend && npm run dev`
Expected: dev server 于 `http://localhost:5173`。

- [ ] **Step 4: 逐条走查验收标准(对照 spec 第 12 节)**

- [ ] 未登录访问 `http://localhost:5173` → 自动跳转 Keycloak 登录页
- [ ] 用 `admin/admin123` 登录 → 回到门户,顶栏显示 `Platform Admin`
- [ ] 新建 Agent,填入 Task 3 Step 6 记下的真实 Chatflow ID → 列表出现该 Agent
- [ ] 新建 Agent 填入不存在的 Chatflow ID → 提示"指定的 Chatflow 不存在"
- [ ] 编辑、删除 Agent → 生效
- [ ] 点"运行"进入 Chat,发送消息 → 回复**逐字流式**出现
- [ ] 连续多轮提问(如先说"我叫张三",再问"我叫什么")→ 第二轮能引用上下文
- [ ] `cd infra && docker compose stop flowise` 后再发消息 → 界面显示 `[运行失败,请重试]` 而非白屏;`docker compose start flowise` 恢复
- [ ] 浏览器手动访问 `http://localhost:8080/api/agents` 且清除会话后 → 返回 401(或经拦截跳登录)

- [ ] **Step 5: 运行后端测试确保回归通过**

Run: `cd backend && mvn -q test`
Expected: 全部通过。

- [ ] **Step 6: 记录验收结果并提交(如有联调期修复)**

```bash
git add -A
git commit -m "chore: end-to-end wiring fixes for vertical slice" || echo "no changes to commit"
```

---

## Self-Review 检查记录

- **Spec 覆盖:** §5 架构→Task 3/6/11;§6 数据模型→Task 8;§7 API(/me、agents CRUD、chat)→Task 7/9/10/12;§8 流程(登录、Chat)→Task 6/11/12;§9 错误处理(401、无效 Chatflow、Flowise 502、SSE 断开、表单校验)→Task 5/6/9/12/14/15/18;§11 测试→各 Task 内含单测 + MockWebServer + spring-security-test;§12 验收→Task 19。无遗漏。
- **占位符:** 通读全篇无 TBD/TODO/"稍后实现";每个代码步骤均给出完整代码。
- **类型一致性:** `ChatflowValidator.chatflowExists`(Task 9 定义)由 `FlowiseClient`(Task 11)实现,签名一致;`AgentService` 构造签名 `(AgentRepository, ChatflowValidator)` 在 Task 9 测试与 Task 10/12 的 `@MockBean` 一致;`streamPrediction(chatflowId, sessionId, question)` 在 Task 11/12 一致;前端 `streamChat(agentId, sessionId, message, cb)` 在 Task 15/18 一致。
