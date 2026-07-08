# Softmatrix — Enterprise Agent Platform (EAP)

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Backend](https://img.shields.io/badge/Backend-Spring_Boot_3.3-6DB33F.svg)](backend)
[![Frontend](https://img.shields.io/badge/Frontend-React_18_·_Ant_Design-0170FE.svg)](frontend)

**English** · [简体中文](README.zh-CN.md)

A unified enterprise platform for **developing, managing, and running AI agents** — built on top of [Flowise](https://flowiseai.com/) (agent workflow engine), [Keycloak](https://www.keycloak.org/) (identity), and an enterprise Portal.

It solves the common enterprise problem of *"agents developed everywhere, running everywhere, with no unified management"* by providing **one portal, one identity, one permission model, one agent platform**.

---

## Overview

Softmatrix EAP gives large enterprises:

- **Unified identity & SSO** — bring your own IAM via OIDC/SAML, or use the bundled Keycloak
- **User, organization & role management** — synced with Keycloak
- **Agent development** — orchestrate flows in an embedded Flowise designer
- **Agent management & runtime** — manage the agent lifecycle and run agents from the Portal
- **A single portal** — users never touch Flowise directly; the Portal enforces all access control

The Portal owns permissions and orchestration; Flowise is the execution engine behind it. Users talk only to the Portal, and the Portal decides what runs.

## Architecture

```
+------------------------------------------------------------+
|                    Enterprise Portal                       |
|   Dashboard | Agent | Workspace | Runtime | Admin          |
+------------------------------------------------------------+
                            |
+------------------------------------------------------------+
|                   Enterprise Services                      |
|   User | Org | Role | Permission | Audit | Config          |
+------------------------------------------------------------+
                            |
                   +------------------+
                   |    Keycloak      |   OIDC / OAuth2 / SAML
                   +------------------+
                            |
+------------------------------------------------------------+
|                      Flowise Engine                        |
|   Workflow | Agent | Tool | Memory | MCP | Runtime         |
+------------------------------------------------------------+
```

The browser talks **only** to the Portal backend (BFF pattern) over an HttpOnly session cookie. Tokens never reach the browser, and Flowise is never exposed to end users.

## Roles

| Role | Responsibility |
|---|---|
| **Platform Admin** | System, user, organization and permission management |
| **Agent Developer** | Create, orchestrate and debug agents and workflows |
| **Business User** | Use agents (cannot modify them) |
| **Auditor** | View logs and run records (read-only) |

## Modules

| # | Module | Description |
|---|---|---|
| 1 | **Unified Login** | Login, SSO and logout — bundled Keycloak or brokered enterprise IAM |
| 2 | **Organization** | Organization / department / position / user tree |
| 3 | **Roles & Permissions** | RBAC over menus, agents and runtime |
| 4 | **Workspace** | Workspace → Agent → Flow, with members and permissions |
| 5 | **Agent Management** | Create, edit, copy, import/export, publish, disable, delete |
| 6 | **Flow Orchestration** | Embedded Flowise designer via the Flowise API |
| 7 | **Agent Runtime** | Run/stop agents; view sessions, logs, tokens and timing |
| 8 | **Dashboard** | Agent count, runs, users, token usage, top agents, failures |

## Project Status

This repository is being built **subproject by subproject** as thin, end-to-end vertical slices. Each subproject ships working, demoable software.

**✅ Subproject 1 — Vertical Slice (implemented)**
The narrowest end-to-end path: **Keycloak login → see an Agent in the Portal → open a chat → run it via Flowise (Azure OpenAI) → stream the reply back**. This proves the two riskiest integrations (Keycloak + Flowise) up front.

See [docs/superpowers/specs/2026-07-07-vertical-slice-design.md](docs/superpowers/specs/2026-07-07-vertical-slice-design.md) for the design and [docs/superpowers/plans/2026-07-07-vertical-slice.md](docs/superpowers/plans/2026-07-07-vertical-slice.md) for the implementation plan.

**🗺️ Roadmap** (each a separate subproject)
1. Embedded Flowise designer + deeper agent management (categories, tags, status, import/export)
2. Organization management + role-based access control (RBAC)
3. Workspace
4. Agent Runtime (sessions, logs, token & timing metrics)
5. Dashboard
6. Enterprise IAM brokering (Keycloak broker, PRD "mode one")

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18, TypeScript, Vite, Ant Design 5 |
| Backend | Spring Boot 3.3 (Java 17), Spring Security OAuth2 Client (BFF), Spring Data JPA |
| Identity | Keycloak |
| Agent Engine | Flowise |
| Data | PostgreSQL |
| LLM | Azure OpenAI (via Flowise) |

## Quick Start

### Prerequisites

- Docker & Docker Compose
- JDK 17 and Maven (the backend targets Java 17)
- Node.js 18+ and npm

### 1. Start the dependencies

```bash
cd infra
docker compose up -d
```

This brings up **Keycloak** (`:8081`), **Flowise** (`:3000`) and **PostgreSQL** (`:5432`). The `softmatrix` Keycloak realm and test users are imported automatically.

| User | Password | Intended role |
|---|---|---|
| `admin` | `admin123` | Platform Admin |
| `user` | `user123` | Business User |

> These are **local-dev placeholder credentials only**. Never reuse them in a real deployment — supply real secrets via environment variables or a secret manager.

### 2. Prepare a demo Chatflow (one-time)

Open Flowise at `http://localhost:3000` (`admin` / `admin`), build a simple **Azure ChatOpenAI → Conversation Chain** flow, fill in your Azure OpenAI endpoint / deployment / key, save it, and copy the **Chatflow ID** — you'll register it in the Portal.

### 3. Run the backend

```bash
cd backend
mvn spring-boot:run
```

Backend starts on `http://localhost:8080`.

### 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`, log in through Keycloak, register an Agent with your Chatflow ID, and start chatting.

### Run the tests

```bash
cd backend && mvn test          # backend unit + web-slice tests (hermetic, no Docker needed)
cd frontend && npm run build    # frontend type-check + production build
```

## Project Structure

```
softmatrix/
├── docs/            # PRD, design specs and implementation plans
├── infra/           # docker-compose: Keycloak + Flowise + PostgreSQL, realm & DB init
├── backend/         # Spring Boot BFF (auth / agent / chat / common)
└── frontend/        # React + Vite + Ant Design portal
```

## License

Licensed under the **Apache License 2.0**. See [LICENSE](LICENSE).

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
