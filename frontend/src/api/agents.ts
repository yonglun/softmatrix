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
