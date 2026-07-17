import type { ReactNode } from 'react';
import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useUser } from './UserContext';

/** 路由级权限守卫:无权限渲染 403,不渲染子组件。 */
export default function RequirePermission({ permission, children }:
    { permission: string; children: ReactNode }) {
  const user = useUser();
  const navigate = useNavigate();
  if (!user.permissions.includes(permission)) {
    return (
      <Result status="403" title="没有访问权限"
        extra={<Button type="primary" onClick={() => navigate('/')}>返回首页</Button>} />
    );
  }
  return <>{children}</>;
}
