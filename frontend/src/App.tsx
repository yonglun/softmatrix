import { useEffect, useState } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Spin } from 'antd';
import { fetchMe } from './api/client';
import type { UserInfo } from './api/types';
import AppLayout from './layouts/AppLayout';
import AgentListPage from './pages/AgentListPage';
import ChatPage from './pages/ChatPage';

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

  return (
    <AppLayout user={user}>
      <Routes>
        <Route path="/" element={<AgentListPage />} />
        <Route path="/agents/:id/chat" element={<ChatPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppLayout>
  );
}
