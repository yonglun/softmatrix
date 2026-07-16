import { client } from './client';
import type { DepartmentNode, DepartmentRequest, Position } from './types';

export async function getDepartmentTree(): Promise<DepartmentNode[]> {
  const { data } = await client.get('/api/departments');
  return data;
}

export async function createDepartment(req: DepartmentRequest): Promise<DepartmentNode> {
  const { data } = await client.post('/api/departments', req);
  return data;
}

export async function updateDepartment(id: string, req: DepartmentRequest): Promise<DepartmentNode> {
  const { data } = await client.put(`/api/departments/${id}`, req);
  return data;
}

export async function deleteDepartment(id: string): Promise<void> {
  await client.delete(`/api/departments/${id}`);
}

export async function listPositions(): Promise<Position[]> {
  const { data } = await client.get('/api/positions');
  return data;
}

export async function createPosition(name: string): Promise<Position> {
  const { data } = await client.post('/api/positions', { name });
  return data;
}

export async function deletePosition(id: string): Promise<void> {
  await client.delete(`/api/positions/${id}`);
}
