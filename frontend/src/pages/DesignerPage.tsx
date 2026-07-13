import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Result, Space, Spin, Tag, Typography, message } from 'antd';
import type { Agent, AppConfig } from '../api/types';
import { getAgent, withdrawAgent } from '../api/agents';
import { getConfig } from '../api/config';
import { STATUS_LABEL } from './agentStatus';

/** 沉浸式设计器页:不套 AppLayout,细顶栏 + iframe 占满视口。 */
export default function DesignerPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [agent, setAgent] = useState<Agent | null>(null);
  const [config, setConfig] = useState<AppConfig | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([getAgent(id), getConfig()])
      .then(([a, c]) => { setAgent(a); setConfig(c); })
      .catch((e) => {
        if (e.response?.status === 404) setNotFound(true);
        else message.error('加载失败');
      })
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const doWithdraw = async () => {
    try { await withdrawAgent(id!); message.success('已撤回为草稿'); load(); }
    catch (e: any) { message.error(e.response?.data?.message ?? '操作失败'); }
  };

  if (loading) return <Spin style={{ marginTop: '20vh', display: 'block' }} />;

  if (notFound || !agent || !config) {
    return (
      <Result status="404" title="Agent 不存在"
        extra={<Button type="primary" onClick={() => navigate('/')}>返回列表</Button>} />
    );
  }

  if (agent.status === 'PUBLISHED') {
    return (
      <Result status="warning" title="该 Agent 已发布"
        subTitle="流程修改保存后立即生效,会直接影响线上对话;请先撤回为草稿再编排。"
        extra={
          <Space>
            <Button type="primary" onClick={doWithdraw}>撤回为草稿</Button>
            <Button onClick={() => navigate('/')}>返回列表</Button>
          </Space>
        } />
    );
  }

  const label = STATUS_LABEL[agent.status];
  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 12,
        padding: '8px 16px', borderBottom: '1px solid #f0f0f0',
      }}>
        <Button size="small" onClick={() => navigate('/')}>← 返回</Button>
        <Typography.Text strong>{agent.name}</Typography.Text>
        <Tag color={label.color}>{label.text}</Tag>
        <Typography.Text type="secondary">在画布中保存后立即生效</Typography.Text>
      </div>
      <iframe
        title="Flowise 设计器"
        src={`${config.designerBaseUrl}/canvas/${agent.flowiseChatflowId}`}
        style={{ flex: 1, border: 'none', width: '100%' }}
      />
    </div>
  );
}
