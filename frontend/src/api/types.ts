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
