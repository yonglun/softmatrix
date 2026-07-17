export interface UserInfo {
  username: string;
  name: string;
  permissions: string[];
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

// GET /api/config 下发的运行时配置
export interface AppConfig {
  designerBaseUrl: string;
}

// ===== 子项目四:组织与 RBAC =====

export interface DepartmentNode {
  id: string;
  name: string;
  parentId: string | null;
  managerUserId: string | null;
  managerName: string | null;
  children: DepartmentNode[];
}

export interface DepartmentRequest {
  name: string;
  parentId?: string | null;
  managerUserId?: string | null;
}

export interface Position {
  id: string;
  name: string;
}

export interface RoleBrief {
  id: string;
  name: string;
}

export interface PortalUser {
  id: string;
  username: string;
  name: string | null;
  email: string | null;
  enabled: boolean;
  departmentId: string | null;
  departmentName: string | null;
  positionId: string | null;
  positionName: string | null;
  roles: RoleBrief[];
}

export interface UserCreateRequest {
  username: string;
  name?: string;
  email?: string;
  password: string;
  departmentId?: string | null;
  positionId?: string | null;
  roleIds?: string[];
}

export interface UserUpdateRequest {
  name?: string;
  email?: string;
  departmentId?: string | null;
  positionId?: string | null;
}

export interface Role {
  id: string;
  name: string;
  description: string | null;
  builtIn: boolean;
  permissions: string[];
  userCount: number;
}

export interface RoleRequest {
  name: string;
  description?: string;
  permissions: string[];
}

export interface PermissionInfo {
  code: string;
  label: string;
  group: string;
}
