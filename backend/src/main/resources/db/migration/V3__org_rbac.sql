-- V3__org_rbac.sql — 子项目四:组织管理与 RBAC
CREATE TABLE department (
    id              uuid PRIMARY KEY,
    name            varchar(100) NOT NULL,
    parent_id       uuid REFERENCES department(id),
    manager_user_id uuid
);

-- position 是 Postgres 保留字,表名用 job_position
CREATE TABLE job_position (
    id   uuid PRIMARY KEY,
    name varchar(50) NOT NULL UNIQUE
);

CREATE TABLE app_user (
    id            uuid PRIMARY KEY,
    keycloak_id   varchar(36) UNIQUE,
    username      varchar(100) NOT NULL UNIQUE,
    name          varchar(100),
    email         varchar(255),
    enabled       boolean NOT NULL DEFAULT true,
    department_id uuid REFERENCES department(id),
    position_id   uuid REFERENCES job_position(id),
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL
);

CREATE TABLE role (
    id          uuid PRIMARY KEY,
    name        varchar(50) NOT NULL UNIQUE,
    description text,
    built_in    boolean NOT NULL DEFAULT false
);

CREATE TABLE role_permission (
    role_id    uuid NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission varchar(64) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE TABLE user_role (
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id uuid NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 种子:根部门(单组织的落地形态,不可删)
INSERT INTO department (id, name) VALUES ('00000000-0000-0000-0000-000000000001', '总部');

-- 种子:内置四角色(built_in 不可编辑不可删除)
INSERT INTO role (id, name, description, built_in) VALUES
  ('10000000-0000-0000-0000-000000000001', 'Platform Admin',  '平台管理员:系统/用户/组织/权限管理', true),
  ('10000000-0000-0000-0000-000000000002', 'Agent Developer', 'Agent 开发者:创建/编排/调试/发布 Agent', true),
  ('10000000-0000-0000-0000-000000000003', 'Business User',   '业务用户:使用 Agent', true),
  ('10000000-0000-0000-0000-000000000004', 'Auditor',         '审计员:全平台只读', true);

INSERT INTO role_permission (role_id, permission) VALUES
  -- Platform Admin:全部 9 项
  ('10000000-0000-0000-0000-000000000001', 'AGENT_VIEW'),
  ('10000000-0000-0000-0000-000000000001', 'AGENT_MANAGE'),
  ('10000000-0000-0000-0000-000000000001', 'AGENT_PUBLISH'),
  ('10000000-0000-0000-0000-000000000001', 'AGENT_DESIGN'),
  ('10000000-0000-0000-0000-000000000001', 'AGENT_RUN'),
  ('10000000-0000-0000-0000-000000000001', 'ORG_VIEW'),
  ('10000000-0000-0000-0000-000000000001', 'ORG_MANAGE'),
  ('10000000-0000-0000-0000-000000000001', 'ROLE_VIEW'),
  ('10000000-0000-0000-0000-000000000001', 'ROLE_MANAGE'),
  -- Agent Developer
  ('10000000-0000-0000-0000-000000000002', 'AGENT_VIEW'),
  ('10000000-0000-0000-0000-000000000002', 'AGENT_MANAGE'),
  ('10000000-0000-0000-0000-000000000002', 'AGENT_PUBLISH'),
  ('10000000-0000-0000-0000-000000000002', 'AGENT_DESIGN'),
  ('10000000-0000-0000-0000-000000000002', 'AGENT_RUN'),
  -- Business User
  ('10000000-0000-0000-0000-000000000003', 'AGENT_VIEW'),
  ('10000000-0000-0000-0000-000000000003', 'AGENT_RUN'),
  -- Auditor
  ('10000000-0000-0000-0000-000000000004', 'AGENT_VIEW'),
  ('10000000-0000-0000-0000-000000000004', 'ORG_VIEW'),
  ('10000000-0000-0000-0000-000000000004', 'ROLE_VIEW');
