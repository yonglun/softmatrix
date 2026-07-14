import { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Table, Button, Modal, Form, Input, Select, AutoComplete, Space, Tag, Popconfirm, message, Collapse,
} from 'antd';
import type { Agent, AgentRequest, AgentFilters, AgentStatus, AgentPackage } from '../api/types';
import {
  listAgents, createAgent, updateAgent, deleteAgent,
  publishAgent, disableAgent, withdrawAgent,
  listCategories, listTags, exportAgent, importAgent,
} from '../api/agents';
import { STATUS_LABEL } from './agentStatus';

export default function AgentListPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<AgentFilters>({});
  const [categories, setCategories] = useState<string[]>([]);
  const [tags, setTags] = useState<string[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Agent | null>(null);
  const [form] = Form.useForm<AgentRequest>();
  const fileInput = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  const reload = useCallback(() => {
    setLoading(true);
    listAgents(filters).then(setAgents).finally(() => setLoading(false));
  }, [filters]);

  useEffect(() => { reload(); }, [reload]);
  useEffect(() => {
    listCategories().then(setCategories);
    listTags().then(setTags);
  }, [agents.length]);

  const openCreate = () => { setEditing(null); form.resetFields(); setModalOpen(true); };
  const openEdit = (a: Agent) => {
    setEditing(a);
    form.setFieldsValue({
      name: a.name, description: a.description ?? '',
      category: a.category ?? undefined, tags: a.tags,
      flowiseChatflowId: a.flowiseChatflowId,
    });
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

  const runAction = async (fn: () => Promise<unknown>, ok: string) => {
    try { await fn(); message.success(ok); reload(); }
    catch (e: any) { message.error(e.response?.data?.message ?? '操作失败'); }
  };

  const doExport = async (a: Agent) => {
    try {
      const pkg = await exportAgent(a.id);
      const blob = new Blob([JSON.stringify(pkg, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `softmatrix-agent-${a.name}.json`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (e: any) {
      message.error(e.response?.data?.message ?? '导出失败');
    }
  };

  const onImportFile = async (file: File) => {
    try {
      const pkg: AgentPackage = JSON.parse(await file.text());
      await importAgent(pkg);
      message.success('导入成功(草稿)');
      reload();
    } catch (e: any) {
      message.error(e.response?.data?.message ?? '导入失败:文件无效');
    }
  };

  const actionsFor = (a: Agent) => {
    const btns = [] as JSX.Element[];
    if (a.status === 'PUBLISHED') {
      btns.push(<Button key="run" size="small" type="link" onClick={() => navigate(`/agents/${a.id}/chat`)}>运行</Button>);
    }
    btns.push(<Button key="edit" size="small" type="link" onClick={() => openEdit(a)}>编辑</Button>);
    if (a.status === 'DRAFT' || a.status === 'DISABLED') {
      btns.push(<Button key="design" size="small" type="link" onClick={() => navigate(`/agents/${a.id}/design`)}>编排</Button>);
      btns.push(<Button key="pub" size="small" type="link" onClick={() => runAction(() => publishAgent(a.id), a.status === 'DISABLED' ? '已重新启用' : '已发布')}>{a.status === 'DISABLED' ? '重新启用' : '发布'}</Button>);
    }
    if (a.status === 'PUBLISHED') {
      btns.push(<Button key="dis" size="small" type="link" onClick={() => runAction(() => disableAgent(a.id), '已停用')}>停用</Button>);
      btns.push(<Button key="wd" size="small" type="link" onClick={() => runAction(() => withdrawAgent(a.id), '已撤回为草稿')}>撤回</Button>);
    }
    btns.push(<Button key="exp" size="small" type="link" onClick={() => doExport(a)}>导出</Button>);
    btns.push(
      <Popconfirm key="del" title="确认删除?" onConfirm={() => runAction(() => deleteAgent(a.id), '已删除')}>
        <Button size="small" type="link" danger>删除</Button>
      </Popconfirm>
    );
    return <Space size={0} wrap>{btns}</Space>;
  };

  return (
    <>
      <Space style={{ marginBottom: 16 }} wrap>
        <Button type="primary" onClick={openCreate}>新建 Agent</Button>
        <Button onClick={() => fileInput.current?.click()}>导入</Button>
        <input ref={fileInput} type="file" accept="application/json" style={{ display: 'none' }}
          onChange={(e) => { const f = e.target.files?.[0]; if (f) onImportFile(f); e.target.value = ''; }} />
        <Input.Search allowClear placeholder="名称关键字" style={{ width: 160 }}
          onSearch={(v) => setFilters((f) => ({ ...f, keyword: v || undefined }))} />
        <Select allowClear placeholder="分类" style={{ width: 120 }}
          options={categories.map((c) => ({ value: c }))}
          onChange={(v) => setFilters((f) => ({ ...f, category: v }))} />
        <Select allowClear placeholder="标签" style={{ width: 120 }}
          options={tags.map((t) => ({ value: t }))}
          onChange={(v) => setFilters((f) => ({ ...f, tag: v }))} />
        <Select allowClear placeholder="状态" style={{ width: 120 }}
          options={[{ value: 'DRAFT', label: '草稿' }, { value: 'PUBLISHED', label: '已发布' }, { value: 'DISABLED', label: '已停用' }]}
          onChange={(v) => setFilters((f) => ({ ...f, status: v as AgentStatus }))} />
      </Space>

      <Table<Agent> rowKey="id" loading={loading} dataSource={agents}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '分类', dataIndex: 'category' },
          { title: '标签', dataIndex: 'tags', render: (t: string[]) => t?.map((x) => <Tag key={x}>{x}</Tag>) },
          { title: '状态', dataIndex: 'status', render: (s: AgentStatus) => <Tag color={STATUS_LABEL[s].color}>{STATUS_LABEL[s].text}</Tag> },
          { title: 'Owner', dataIndex: 'owner' },
          { title: '操作', render: (_, a) => actionsFor(a) },
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
          <Form.Item name="category" label="分类">
            <AutoComplete options={categories.map((c) => ({ value: c }))}
              placeholder="选择或输入分类" filterOption
              allowClear />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Select mode="tags" placeholder="输入标签,回车添加"
              options={tags.map((t) => ({ value: t }))} />
          </Form.Item>
          {!editing && (
            <Collapse ghost items={[{
              key: 'advanced',
              label: '高级选项:绑定已有 Chatflow',
              children: (
                <Form.Item name="flowiseChatflowId" label="Flowise Chatflow ID"
                  rules={[{ max: 64 }]}
                  extra="留空将自动在 Flowise 创建空白流程">
                  <Input placeholder="从 Flowise 复制的 Chatflow ID" />
                </Form.Item>
              ),
            }]} />
          )}
        </Form>
      </Modal>
    </>
  );
}
