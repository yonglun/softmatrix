import { client } from './client';
import type { PermissionInfo, Role, RoleRequest } from './types';

export async function listRoles(): Promise<Role[]> {
  const { data } = await client.get('/api/roles');
  return data;
}

export async function createRole(req: RoleRequest): Promise<Role> {
  const { data } = await client.post('/api/roles', req);
  return data;
}

export async function updateRole(id: string, req: RoleRequest): Promise<Role> {
  const { data } = await client.put(`/api/roles/${id}`, req);
  return data;
}

export async function deleteRole(id: string): Promise<void> {
  await client.delete(`/api/roles/${id}`);
}

export async function listPermissions(): Promise<PermissionInfo[]> {
  const { data } = await client.get('/api/permissions');
  return data;
}
