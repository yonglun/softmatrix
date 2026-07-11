import { client } from './client';
import type { Agent, AgentRequest, AgentFilters, AgentPackage } from './types';

export async function listAgents(filters: AgentFilters = {}): Promise<Agent[]> {
  const { data } = await client.get('/api/agents', { params: filters });
  return data;
}

export async function listCategories(): Promise<string[]> {
  const { data } = await client.get('/api/agents/categories');
  return data;
}

export async function listTags(): Promise<string[]> {
  const { data } = await client.get('/api/agents/tags');
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

export async function publishAgent(id: string): Promise<Agent> {
  const { data } = await client.post(`/api/agents/${id}/publish`);
  return data;
}

export async function disableAgent(id: string): Promise<Agent> {
  const { data } = await client.post(`/api/agents/${id}/disable`);
  return data;
}

export async function withdrawAgent(id: string): Promise<Agent> {
  const { data } = await client.post(`/api/agents/${id}/withdraw`);
  return data;
}

export async function exportAgent(id: string): Promise<AgentPackage> {
  const { data } = await client.get(`/api/agents/${id}/export`);
  return data;
}

export async function importAgent(pkg: AgentPackage): Promise<Agent> {
  const { data } = await client.post('/api/agents/import', pkg);
  return data;
}
