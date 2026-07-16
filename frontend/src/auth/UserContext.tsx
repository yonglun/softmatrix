import { createContext, useContext } from 'react';
import type { UserInfo } from '../api/types';

const UserContext = createContext<UserInfo | null>(null);

export const UserProvider = UserContext.Provider;

export function useUser(): UserInfo {
  const user = useContext(UserContext);
  if (!user) throw new Error('UserProvider 未挂载');
  return user;
}

/** 返回判权函数:hasPerm('AGENT_MANAGE') */
export function useHasPermission(): (permission: string) => boolean {
  const user = useUser();
  return (permission) => user.permissions.includes(permission);
}
