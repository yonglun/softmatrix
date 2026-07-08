import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Button, Modal, Form, Input, Space, Popconfirm, message } from 'antd';
import type { Agent, AgentRequest } from '../api/types';
import { listAgents, createAgent, updateAgent, deleteAgent } from '../api/agents';

export default function AgentListPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Agent | null>(null);
  const [form] = Form.useForm<AgentRequest>();
  const navigate = useNavigate();

  const reload = () => {
    setLoading(true);
    listAgents().then(setAgents).finally(() => setLoading(false));
  };

  useEffect(reload, []);

  const openCreate = () => { setEditing(null); form.resetFields(); setModalOpen(true); };
  const openEdit = (a: Agent) => {
    setEditing(a);
    form.setFieldsValue({ name: a.name, description: a.description ?? '', flowiseChatflowId: a.flowiseChatflowId });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    try {
      if (editing) await updateAgent(editing.id, values);
      else await createAgent(values);
      message.success('保存成功');
      setModalOpen(false);
      reload();
    } catch (e: any) {
      message.error(e.response?.data?.message ?? '保存失败');
    }
  };

  const remove = async (id: string) => {
    await deleteAgent(id);
    message.success('已删除');
    reload();
  };

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={openCreate}>新建 Agent</Button>
      </Space>
      <Table<Agent> rowKey="id" loading={loading} dataSource={agents}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '描述', dataIndex: 'description' },
          { title: 'Chatflow ID', dataIndex: 'flowiseChatflowId' },
          { title: 'Owner', dataIndex: 'owner' },
          {
            title: '操作',
            render: (_, a) => (
              <Space>
                <Button size="small" type="link" onClick={() => navigate(`/agents/${a.id}/chat`)}>运行</Button>
                <Button size="small" type="link" onClick={() => openEdit(a)}>编辑</Button>
                <Popconfirm title="确认删除?" onConfirm={() => remove(a.id)}>
                  <Button size="small" type="link" danger>删除</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
      <Modal title={editing ? '编辑 Agent' : '新建 Agent'} open={modalOpen}
        onOk={submit} onCancel={() => setModalOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, max: 100 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="flowiseChatflowId" label="Flowise Chatflow ID"
            rules={[{ required: true, max: 64 }]}>
            <Input placeholder="从 Flowise 复制的 Chatflow ID" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
