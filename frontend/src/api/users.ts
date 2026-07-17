import { client } from './client';
import type { PortalUser, UserCreateRequest, UserUpdateRequest } from './types';

export async function listUsers(params: {
  dept?: string; keyword?: string; enabled?: boolean;
} = {}): Promise<PortalUser[]> {
  const { data } = await client.get('/api/users', { params });
  return data;
}

export async function createUser(req: UserCreateRequest): Promise<PortalUser> {
  const { data } = await client.post('/api/users', req);
  return data;
}

export async function updateUser(id: string, req: UserUpdateRequest): Promise<PortalUser> {
  const { data } = await client.put(`/api/users/${id}`, req);
  return data;
}

export async function setUserEnabled(id: string, enabled: boolean): Promise<PortalUser> {
  const { data } = await client.put(`/api/users/${id}/enabled`, { enabled });
  return data;
}

export async function resetUserPassword(id: string, password: string): Promise<void> {
  await client.put(`/api/users/${id}/password`, { password });
}

export async function setUserRoles(id: string, roleIds: string[]): Promise<PortalUser> {
  const { data } = await client.put(`/api/users/${id}/roles`, { roleIds });
  return data;
}
