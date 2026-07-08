import { Layout, Typography, Button, Space } from 'antd';
import type { ReactNode } from 'react';
import type { UserInfo } from '../api/types';
import { logout } from '../api/client';

const { Header, Content } = Layout;

export default function AppLayout({ user, children }: { user: UserInfo; children: ReactNode }) {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ color: '#fff', margin: 0 }}>
          Softmatrix EAP
        </Typography.Title>
        <Space>
          <span style={{ color: '#fff' }}>{user.name || user.username}</span>
          <Button size="small" onClick={logout}>登出</Button>
        </Space>
      </Header>
      <Content style={{ padding: 24 }}>{children}</Content>
    </Layout>
  );
}
