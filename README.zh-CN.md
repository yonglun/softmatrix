# Softmatrix — 企业智能体平台（EAP）

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Backend](https://img.shields.io/badge/Backend-Spring_Boot_3.3-6DB33F.svg)](backend)
[![Frontend](https://img.shields.io/badge/Frontend-React_18_·_Ant_Design-0170FE.svg)](frontend)

[English](README.md) · **简体中文**

一款面向大型企业的智能体**开发、管理与运行**统一平台，基于 [Flowise](https://flowiseai.com/)（智能体工作流引擎）、[Keycloak](https://www.keycloak.org/)（身份认证）与企业门户（Portal）构建。

它解决企业普遍存在的*"Agent 到处开发、到处运行、没有统一管理"*问题，提供**一个门户、一个身份、一个权限体系、一个 Agent 平台**。

---

## 产品简介

Softmatrix EAP 为大型企业提供：

- **统一身份与单点登录** —— 通过 OIDC/SAML 对接企业已有 IAM，或使用平台自带的 Keycloak
- **用户、组织与角色管理** —— 与 Keycloak 同步
- **智能体开发** —— 在嵌入的 Flowise 设计器中编排工作流
- **智能体管理与运行** —— 在门户中管理 Agent 生命周期并运行 Agent
- **统一门户** —— 用户无需直接接触 Flowise，所有访问控制由门户统一实施

门户负责权限与编排，Flowise 作为背后的执行引擎。用户只与门户交互，由门户决定运行什么。

## 产品架构

```
+------------------------------------------------------------+
|                    企业门户 Portal                          |
|   Dashboard | Agent | Workspace | Runtime | Admin          |
+------------------------------------------------------------+
                            |
+------------------------------------------------------------+
|                   企业服务 Enterprise Services              |
|   User | Org | Role | Permission | Audit | Config          |
+------------------------------------------------------------+
                            |
                   +------------------+
                   |    Keycloak      |   OIDC / OAuth2 / SAML
                   +------------------+
                            |
+------------------------------------------------------------+
|                    Flowise 引擎                             |
|   Workflow | Agent | Tool | Memory | MCP | Runtime         |
+------------------------------------------------------------+
```

浏览器**只**与门户后端通信（BFF 模式），使用 HttpOnly 会话 Cookie，Token 不进入浏览器，Flowise 也不会暴露给最终用户。

## 产品角色

| 角色 | 职责 |
|---|---|
| **Platform Admin** | 系统、用户、组织与权限管理 |
| **Agent Developer** | 创建、编排、调试 Agent 与工作流 |
| **Business User** | 使用 Agent（不能修改） |
| **Auditor** | 查看日志与运行记录（只读） |

## 功能模块

| # | 模块 | 说明 |
|---|---|---|
| 1 | **统一登录** | 登录、单点登录、登出 —— 自带 Keycloak 或对接企业 IAM |
| 2 | **组织管理** | 组织 / 部门 / 岗位 / 用户 树形结构 |
| 3 | **角色权限** | 基于 RBAC 的菜单、Agent、运行权限 |
| 4 | **Workspace** | Workspace → Agent → Flow，含成员与权限 |
| 5 | **Agent 管理** | 创建、编辑、复制、导入导出、发布、停用、删除 |
| 6 | **Flow 编排** | 通过 Flowise API 嵌入 Flowise 设计器 |
| 7 | **Agent Runtime** | 运行/停止 Agent；查看会话、日志、Token、耗时 |
| 8 | **Dashboard** | Agent 数、运行次数、用户数、Token、Top Agent、失败次数 |

## 项目进度

本仓库按**垂直切片**策略、一个子项目一个子项目地推进，每个子项目都交付可运行、可演示的软件。

**✅ 子项目一 —— 垂直切片（已实现）**
最窄的端到端链路：**Keycloak 登录 → 在门户中看到一个 Agent → 打开对话窗口 → 经 Flowise（Azure OpenAI）运行 → 流式返回回复**。目的是在第一个子项目就打通并暴露两个最大的集成风险（Keycloak + Flowise）。

设计文档见 [docs/superpowers/specs/2026-07-07-vertical-slice-design.md](docs/superpowers/specs/2026-07-07-vertical-slice-design.md)，实现计划见 [docs/superpowers/plans/2026-07-07-vertical-slice.md](docs/superpowers/plans/2026-07-07-vertical-slice.md)。

**🗺️ 路线图**（每项为一个独立子项目）
1. 嵌入 Flowise 设计器 + Agent 管理加深（分类、标签、状态、导入导出）
2. 组织管理 + 角色权限（RBAC）
3. Workspace
4. Agent Runtime（会话、日志、Token 与耗时指标）
5. Dashboard
6. 企业 IAM 对接（Keycloak Broker，即 PRD "模式一"）

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | React 18、TypeScript、Vite、Ant Design 5 |
| 后端 | Spring Boot 3.3（Java 17）、Spring Security OAuth2 Client（BFF）、Spring Data JPA |
| 身份认证 | Keycloak |
| 智能体引擎 | Flowise |
| 数据库 | PostgreSQL |
| 大模型 | Azure OpenAI（经 Flowise） |

## 快速开始

### 前置条件

- Docker 与 Docker Compose
- JDK 17 与 Maven（后端目标为 Java 17）
- Node.js 18+ 与 npm

### 1. 启动依赖服务

```bash
cd infra
docker compose up -d
```

将启动 **Keycloak**（`:8081`）、**Flowise**（`:3000`）与 **PostgreSQL**（`:5432`）。`softmatrix` Keycloak realm 与测试用户会自动导入。

| 用户 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | Platform Admin |
| `user` | `user123` | Business User |

> 这些**仅为本地开发用的占位凭据**。请勿在真实部署中沿用，务必通过环境变量或密钥管理注入真实密钥。

### 2. 准备演示 Chatflow（一次性）

打开 Flowise `http://localhost:3000`（`admin` / `admin`），新建一个 **Azure ChatOpenAI → Conversation Chain** 流程，填入你的 Azure OpenAI endpoint / deployment / key 并保存，复制该 **Chatflow ID** —— 稍后在门户中登记它。

### 3. 运行后端

```bash
cd backend
mvn spring-boot:run
```

后端启动在 `http://localhost:8080`。

### 4. 运行前端

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`，经 Keycloak 登录，用你的 Chatflow ID 登记一个 Agent，即可开始对话。

### 运行测试

```bash
cd backend && mvn test          # 后端单元与 Web 切片测试（自包含，无需 Docker）
cd frontend && npm run build    # 前端类型检查 + 生产构建
```

## 项目结构

```
softmatrix/
├── docs/            # PRD、设计文档与实现计划
├── infra/           # docker-compose：Keycloak + Flowise + PostgreSQL，realm 与数据库初始化
├── backend/         # Spring Boot BFF（auth / agent / chat / common）
└── frontend/        # React + Vite + Ant Design 门户
```

## 许可证

采用 **Apache License 2.0** 许可证，详见 [LICENSE](LICENSE)。

```
Copyright 2026 Softmatrix

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
```
