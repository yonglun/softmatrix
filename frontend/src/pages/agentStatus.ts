import type { AgentStatus } from '../api/types';

export const STATUS_LABEL: Record<AgentStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  PUBLISHED: { text: '已发布', color: 'green' },
  DISABLED: { text: '已停用', color: 'red' },
};
