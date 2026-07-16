import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button, Checkbox, Form, Input, Modal, Popconfirm, Space, Table, Tag, message,
} from 'antd';
import type { PermissionInfo, Role } from '../api/types';
import { createRole, deleteRole, listPermissions, listRoles, updateRole } from '../api/roles';
import { useHasPermission } from '../auth/UserContext';

export default function RolesPage() {
  const has = useHasPermission();
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<PermissionInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Role | null>(null);
  const [viewing, setViewing] = useState<Role | null>(null);
  const [form] = Form.useForm();

  const reload = useCallback(() => {
    setLoading(true);
    listRoles().then(setRoles).finally(() => setLoading(false));
  }, []);
  useEffect(() => {
    reload();
    listPermissions().then(setPermissions);
  }, [reload]);

  const groups = useMemo(() => {
    const m = new Map<string, PermissionInfo[]>();
    permissions.forEach((p) => {
      m.set(p.group, [...(m.get(p.group) ?? []), p]);
    });
    return [...m.entries()];
  }, [permissions]);

  const labelOf = (code: string) => permissions.find((p) => p.code === code)?.label ?? code;

  const openCreate = () => { setEditing(null); form.resetFields(); setModalOpen(true); };
  const openEdit = (r: Role) => {
    setEditing(r);
    form.setFieldsValue({ name: r.name, description: r.description ?? '', permissions: r.permissions });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    try {
      if (editing) await updateRole(editing.id, values);
      else await createRole(values);
      message.success('保存成功');
      setModalOpen(false);
      reload();
    } catch (e: any) {
      message.error(e.response?.data?.message ?? '保存失败');
    }
  };

  const remove = async (r: Role) => {
    try { await deleteRole(r.id); message.success('已删除'); reload(); }
    catch (e: any) { message.error(e.response?.data?.message ?? '删除失败'); }
  };

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        {has('ROLE_MANAGE') && <Button type="primary" onClick={openCreate}>新建角色</Button>}
      </Space>
      <Table<Role> rowKey="id" loading={loading} dataSource={roles}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '描述', dataIndex: 'description' },
          { title: '类型', dataIndex: 'builtIn', render: (b: boolean) => b ? <Tag color="blue">内置</Tag> : <Tag>自定义</Tag> },
          { title: '权限数', dataIndex: 'permissions', render: (p: string[]) => p.length },
          { title: '用户数', dataIndex: 'userCount' },
          {
            title: '操作',
            render: (_, r) => (
              <Space size={0} wrap>
                <Button size="small" type="link" onClick={() => setViewing(r)}>查看权限</Button>
                {has('ROLE_MANAGE') && !r.builtIn && (
                  <Button size="small" type="link" onClick={() => openEdit(r)}>编辑</Button>
                )}
                {has('ROLE_MANAGE') && !r.builtIn && (
                  <Popconfirm title="确认删除?" onConfirm={() => remove(r)}>
                    <Button size="small" type="link" danger>删除</Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]} />

      <Modal title={editing ? '编辑角色' : '新建角色'} open={modalOpen}
        onOk={submit} onCancel={() => setModalOpen(false)} destroyOnClose width={560}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, max: 50 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="permissions" label="权限" rules={[{ required: true, message: '至少选择一项权限' }]}>
            <Checkbox.Group style={{ width: '100%' }}>
              {groups.map(([group, items]) => (
                <div key={group} style={{ marginBottom: 8 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>{group}</div>
                  <Space wrap>
                    {items.map((p) => <Checkbox key={p.code} value={p.code}>{p.label}</Checkbox>)}
                  </Space>
                </div>
              ))}
            </Checkbox.Group>
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={`权限:${viewing?.name ?? ''}`} open={!!viewing} footer={null}
        onCancel={() => setViewing(null)}>
        <Space wrap>
          {viewing?.permissions.map((c) => <Tag key={c}>{labelOf(c)}</Tag>)}
        </Space>
      </Modal>
    </>
  );
}
