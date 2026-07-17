import { useEffect, useState } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Spin } from 'antd';
import { fetchMe } from './api/client';
import type { UserInfo } from './api/types';
import { UserProvider } from './auth/UserContext';
import RequirePermission from './auth/RequirePermission';
import AppLayout from './layouts/AppLayout';
import AgentListPage from './pages/AgentListPage';
import ChatPage from './pages/ChatPage';
import DesignerPage from './pages/DesignerPage';
import NoPermissionPage from './pages/NoPermissionPage';
import OrgPage from './pages/org/OrgPage';
import RolesPage from './pages/RolesPage';

export default function App() {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchMe()
      .then(setUser)
      .catch(() => { /* 401 拦截器已跳转登录 */ })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Spin style={{ marginTop: '20vh', display: 'block' }} />;
  if (!user) return null; // 正在跳转登录
  if (user.permissions.length === 0) return <NoPermissionPage />;

  return (
    <UserProvider value={user}>
      <Routes>
        <Route path="/agents/:id/design" element={
          <RequirePermission permission="AGENT_DESIGN"><DesignerPage /></RequirePermission>} />
        <Route path="/" element={
          <AppLayout><RequirePermission permission="AGENT_VIEW"><AgentListPage /></RequirePermission></AppLayout>} />
        <Route path="/agents/:id/chat" element={
          <AppLayout><RequirePermission permission="AGENT_RUN"><ChatPage /></RequirePermission></AppLayout>} />
        <Route path="/admin/org" element={
          <AppLayout><RequirePermission permission="ORG_VIEW"><OrgPage /></RequirePermission></AppLayout>} />
        <Route path="/admin/roles" element={
          <AppLayout><RequirePermission permission="ROLE_VIEW"><RolesPage /></RequirePermission></AppLayout>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </UserProvider>
  );
}
