import { Layout, Typography, Button, Space, Menu } from 'antd';
import type { ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { logout } from '../api/client';
import { useUser } from '../auth/UserContext';

const { Header, Content } = Layout;

const NAV = [
  { key: '/', label: 'Agent', permission: 'AGENT_VIEW' },
  { key: '/admin/org', label: '组织与用户', permission: 'ORG_VIEW' },
  { key: '/admin/roles', label: '角色', permission: 'ROLE_VIEW' },
];

export default function AppLayout({ children }: { children: ReactNode }) {
  const user = useUser();
  const navigate = useNavigate();
  const location = useLocation();

  const items = NAV.filter((n) => user.permissions.includes(n.permission))
    .map(({ key, label }) => ({ key, label }));
  const selected = NAV.map((n) => n.key)
    .filter((k) => (k === '/' ? location.pathname === '/' : location.pathname.startsWith(k)));

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
        <Typography.Title level={4} style={{ color: '#fff', margin: 0, whiteSpace: 'nowrap' }}>
          Softmatrix EAP
        </Typography.Title>
        <Menu theme="dark" mode="horizontal" items={items} selectedKeys={selected}
          onClick={(e) => navigate(e.key)} style={{ flex: 1, minWidth: 0 }} />
        <Space>
          <span style={{ color: '#fff' }}>{user.name || user.username}</span>
          <Button size="small" onClick={logout}>登出</Button>
        </Space>
      </Header>
      <Content style={{ padding: 24 }}>{children}</Content>
    </Layout>
  );
}
