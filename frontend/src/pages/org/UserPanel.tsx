import { useCallback, useEffect, useState } from 'react';
import {
  Button, Form, Input, Modal, Select, Space, Table, Tag, message,
} from 'antd';
import type { DepartmentNode, PortalUser, Position, Role } from '../../api/types';
import { listPositions } from '../../api/org';
import {
  createUser, listUsers, resetUserPassword, setUserEnabled, setUserRoles, updateUser,
} from '../../api/users';
import { listRoles } from '../../api/roles';
import { useHasPermission, useUser } from '../../auth/UserContext';

export default function UserPanel({ departmentId, departments, onUsersChanged }: {
  departmentId: string | null;
  departments: DepartmentNode[];
  onUsersChanged: () => void;
}) {
  const has = useHasPermission();
  const me = useUser();
  const [users, setUsers] = useState<PortalUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState<string>();
  const [positions, setPositions] = useState<Position[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [userModalOpen, setUserModalOpen] = useState(false);
  const [editing, setEditing] = useState<PortalUser | null>(null);
  const [rolesTarget, setRolesTarget] = useState<PortalUser | null>(null);
  const [pwdTarget, setPwdTarget] = useState<PortalUser | null>(null);
  const [form] = Form.useForm();
  const [rolesForm] = Form.useForm();
  const [pwdForm] = Form.useForm();

  const reload = useCallback(() => {
    setLoading(true);
    listUsers({ dept: departmentId ?? undefined, keyword })
      .then(setUsers).finally(() => setLoading(false));
  }, [departmentId, keyword]);
  useEffect(() => { reload(); }, [reload]);
  useEffect(() => {
    listPositions().then(setPositions);
    if (has('ROLE_VIEW')) listRoles().then(setRoles);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ departmentId });
    setUserModalOpen(true);
  };
  const openEdit = (u: PortalUser) => {
    setEditing(u);
    form.setFieldsValue({
      username: u.username, name: u.name ?? '', email: u.email ?? '',
      departmentId: u.departmentId, positionId: u.positionId,
    });
    setUserModalOpen(true);
  };

  const submitUser = async () => {
    const values = await form.validateFields();
    try {
      if (editing) await updateUser(editing.id, values);
      else await createUser(values);
      message.success('保存成功');
      setUserModalOpen(false);
      reload();
      onUsersChanged();
    } catch (e: any) {
      message.error(e.response?.data?.message ?? '保存失败');
    }
  };

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    try { await fn(); message.success(ok); reload(); }
    catch (e: any) { message.error(e.response?.data?.message ?? '操作失败'); }
  };

  const submitRoles = async () => {
    const { roleIds } = await rolesForm.validateFields();
    await run(() => setUserRoles(rolesTarget!.id, roleIds), '角色已更新');
    setRolesTarget(null);
  };

  const submitPwd = async () => {
    const { password } = await pwdForm.validateFields();
    await run(() => resetUserPassword(pwdTarget!.id, password), '密码已重置(首次登录需修改)');
    setPwdTarget(null);
  };

  return (
    <>
      <Space style={{ marginBottom: 12 }} wrap>
        {has('ORG_MANAGE') && <Button type="primary" onClick={openCreate}>新建用户</Button>}
        <Input.Search allowClear placeholder="用户名/姓名" style={{ width: 200 }}
          onSearch={(v) => setKeyword(v || undefined)} />
      </Space>
      <Table<PortalUser> rowKey="id" loading={loading} dataSource={users} size="small"
        columns={[
          { title: '用户名', dataIndex: 'username' },
          { title: '姓名', dataIndex: 'name' },
          { title: '部门', dataIndex: 'departmentName' },
          { title: '岗位', dataIndex: 'positionName' },
          { title: '角色', dataIndex: 'roles', render: (rs: PortalUser['roles']) => rs.map((r) => <Tag key={r.id}>{r.name}</Tag>) },
          { title: '状态', dataIndex: 'enabled', render: (e: boolean) => e ? <Tag color="green">启用</Tag> : <Tag color="red">停用</Tag> },
          {
            title: '操作',
            render: (_, u) => (
              <Space size={0} wrap>
                {has('ORG_MANAGE') && <Button size="small" type="link" onClick={() => openEdit(u)}>编辑</Button>}
                {has('ROLE_MANAGE') && (
                  <Button size="small" type="link" onClick={() => {
                    setRolesTarget(u);
                    rolesForm.setFieldsValue({ roleIds: u.roles.map((r) => r.id) });
                  }}>分配角色</Button>
                )}
                {has('ORG_MANAGE') && <Button size="small" type="link" onClick={() => { setPwdTarget(u); pwdForm.resetFields(); }}>重置密码</Button>}
                {has('ORG_MANAGE') && u.username !== me.username && (
                  <Button size="small" type="link" danger={u.enabled}
                    onClick={() => run(() => setUserEnabled(u.id, !u.enabled), u.enabled ? '已停用' : '已启用')}>
                    {u.enabled ? '停用' : '启用'}
                  </Button>
                )}
              </Space>
            ),
          },
        ]} />

      <Modal title={editing ? '编辑用户' : '新建用户'} open={userModalOpen}
        onOk={submitUser} onCancel={() => setUserModalOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, max: 100 }]}>
            <Input disabled={!!editing} />
          </Form.Item>
          <Form.Item name="name" label="姓名" rules={[{ max: 100 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ type: 'email', max: 255 }]}>
            <Input />
          </Form.Item>
          {!editing && (
            <Form.Item name="password" label="初始密码(用户首次登录需修改)"
              rules={[{ required: true, min: 8, max: 64 }]}>
              <Input.Password />
            </Form.Item>
          )}
          <Form.Item name="departmentId" label="部门">
            <Select allowClear options={departments.map((d) => ({ value: d.id, label: d.name }))} />
          </Form.Item>
          <Form.Item name="positionId" label="岗位">
            <Select allowClear options={positions.map((p) => ({ value: p.id, label: p.name }))} />
          </Form.Item>
          {!editing && has('ROLE_MANAGE') && (
            <Form.Item name="roleIds" label="角色">
              <Select mode="multiple" options={roles.map((r) => ({ value: r.id, label: r.name }))} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal title={`分配角色:${rolesTarget?.username ?? ''}`} open={!!rolesTarget}
        onOk={submitRoles} onCancel={() => setRolesTarget(null)} destroyOnClose>
        <Form form={rolesForm} layout="vertical">
          <Form.Item name="roleIds" label="角色">
            <Select mode="multiple" options={roles.map((r) => ({ value: r.id, label: r.name }))} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={`重置密码:${pwdTarget?.username ?? ''}`} open={!!pwdTarget}
        onOk={submitPwd} onCancel={() => setPwdTarget(null)} destroyOnClose>
        <Form form={pwdForm} layout="vertical">
          <Form.Item name="password" label="新密码(用户首次登录需修改)"
            rules={[{ required: true, min: 8, max: 64 }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
