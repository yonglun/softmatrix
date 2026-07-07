# Softmatrix Enterprise Agent Platform

# Softmatrix **Enterprise Agent Platform  Product Requirement Document (PRD)**

**Version**

V1.0 MVP

---

# **1. 产品定位**

## **1.1 产品简介**

Enterprise Agent Platform（EAP）是一款面向大型企业的智能体开发、管理和运行平台。

平台基于：

- Flowise（Agent Workflow Engine）
- Keycloak（IAM）
- Portal（Enterprise Portal）

构建统一的企业级智能体平台，为企业提供：

- 企业统一身份认证
- 用户、组织、角色管理
- 智能体开发
- 智能体管理
- 智能体运行
- 智能体统一门户

支持企业已有 IAM 集成，也支持平台自带 IAM。

---

## **1.2 产品目标**

建立企业统一 Agent 平台。

解决企业：

“Agent 到处开发，到处运行，没有统一管理”

的问题。

实现：

- 一个门户
- 一个身份
- 一个权限体系
- 一个 Agent 平台

---

# **2. 产品架构**

```
+------------------------------------------------------------+
|                  Enterprise Portal                         |
|------------------------------------------------------------|
| Dashboard | Agent | Workspace | Runtime | Admin            |
+------------------------------------------------------------+
                           |
+------------------------------------------------------------+
|                     Enterprise Services                    |
|------------------------------------------------------------|
| User | Org | Role | Permission | Audit | Config            |
+------------------------------------------------------------+
                           |
                 +------------------+
                 |    Keycloak      |
                 +------------------+
                           |
                 OIDC / OAuth2 / SAML
                           |
+------------------------------------------------------------+
|                     Flowise Engine                         |
|------------------------------------------------------------|
| Workflow | Agent | Tool | Memory | MCP | Runtime           |
+------------------------------------------------------------+
```

---

# **3. 产品角色**

建议只有四类角色。

## **Platform Admin**

负责：

- 系统管理
- 用户管理
- 组织管理
- 权限管理

---

## **Agent Developer**

负责：

- 创建 Agent
- 创建 Workflow
- 调试 Agent

---

## **Business User**

负责：

- 使用 Agent

不能修改 Agent。

---

## **Auditor**

负责：

- 查看日志
- 查看运行记录

不能修改数据。

---

# **4. 功能模块**

建议控制在 **8 个模块**。

---

## **模块一：统一登录**

### **功能**

支持：

- 用户登录
- 单点登录
- 登出

### **登录模式**

#### **模式一**

企业已有 IAM：

```
AD

↓

Keycloak Broker

↓

Portal
```

#### **模式二**

没有 IAM：

```
Keycloak

↓

Portal
```

---

## **模块二：组织管理**

支持：

```
组织

部门

岗位（可选）

用户
```

例如：

```
总部

├──研发

├──销售

└──实施
```

功能：

新增

编辑

删除

移动

负责人

---

## **模块三：角色权限**

支持：

RBAC。

默认角色：

```
Platform Admin

Workspace Admin

Developer

Operator

Viewer
```

支持：

菜单权限

Agent权限

运行权限

---

## **模块四：Workspace**

支持：

```
Workspace

↓

Agent

↓

Flow
```

每个 Workspace：

拥有：

成员

权限

Agent

Flow

---

## **模块五：Agent 管理**

这是核心。

支持：

创建

编辑

复制

导入

导出

发布

停用

删除

Agent 信息：

```
名称

描述

Owner

分类

标签

状态

创建时间

更新时间
```

---

## **模块六：Flow 编排**

调用：

Flowise API。

支持：

创建 Workflow

修改 Workflow

运行 Workflow

查看版本

导入 JSON

导出 JSON

Portal 不重新实现 Designer。

Designer：

直接嵌入：

Flowise。

---

## **模块七：Agent Runtime**

支持：

Agent：

运行

停止

查看历史

查看 Session

查看日志

查看 Token

查看耗时

---

## **模块八：Dashboard**

首页：

显示：

```
Agent 数

运行次数

用户数

Token

Top Agent

失败次数

运行时间
```

---

# **5. 权限模型**

建议：

Portal 做权限。

Flowise：

只负责运行。

例如：

```
Portal

↓

判断权限

↓

Flowise API

↓

运行 Agent
```

不要让：

用户：

直接访问：

Flowise。

---

# **6. 页面规划**

建议：

## **登录**

```
Login
```

---

## **首页**

```
Dashboard
```

---

## **工作空间**

```
Workspace List
```

---

## **Agent**

```
Agent List

↓

Agent Detail

↓

Run
```

---

## **Workflow**

```
Flow List

↓

Open Flowise
```

---

## **用户**

```
User List
```

---

## **部门**

```
Department
```

---

## **角色**

```
Role
```

---

## **系统配置**

```
System

SSO

License

Audit
```

---

# **7. 技术架构**

Portal：

建议：

React

Ant Design

或：

Vue3

Naive UI

Backend：

Spring Boot

Flowise：

NodeJS

Keycloak：

IAM

数据库：

PostgreSQL

Redis

---

# **8. MVP 不做**

为了控制项目规模，建议以下功能暂不纳入 V1：

- Skill Center
- Digital Employee
- AI Marketplace
- Knowledge Center（仅复用 Flowise 能力）
- Prompt Center
- Model Gateway
- Agent 审批
- Agent 版本治理
- 企业 IM（Teams、飞书、企业微信等）
- 多 Agent 协同
- 多 Runtime 支持
- 成本中心与配额管理
- 多租户 SaaS

---

# **9. V1 验收标准（Definition of Done）**

平台达到以下能力即可视为 MVP 完成：

| **能力** | **验收标准** |
| --- | --- |
| 身份认证 | 支持 Keycloak 登录，并支持通过 OIDC/SAML 对接企业现有 IAM（如有） |
| 用户与组织 | 支持用户、部门、角色、组织管理，并与 Keycloak 同步 |
| 权限控制 | Portal 实现统一 RBAC，限制 Agent、Workspace、管理功能访问 |
| Agent 开发 | 能在 Portal 中管理 Agent，并调用嵌入的 Flowise Designer 进行编排 |
| Agent 运行 | Portal 可启动、停止、查看运行记录、查看日志 |
| 统一门户 | 用户无需直接访问 Flowise，即可完成大部分日常操作 |
| 可扩展性 | Portal 与 Flowise 解耦，通过 API 通信，为后续接入 Skill、Digital Employee、企业 IM 等能力预留接口 |

---