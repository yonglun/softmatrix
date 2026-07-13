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

// GET /api/config 下发的运行时配置
export interface AppConfig {
  designerBaseUrl: string;
}
