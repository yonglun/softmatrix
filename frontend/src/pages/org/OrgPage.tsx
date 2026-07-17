import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button, Card, Col, Form, Input, Modal, Popconfirm, Row, Select, Space, Tree, TreeSelect, message,
} from 'antd';
import type { DepartmentNode, PortalUser } from '../../api/types';
import { createDepartment, deleteDepartment, getDepartmentTree, updateDepartment } from '../../api/org';
import { listUsers } from '../../api/users';
import { useHasPermission } from '../../auth/UserContext';
import UserPanel from './UserPanel';
import PositionModal from './PositionModal';

// AntD Tree/TreeSelect 的数据结构
function toTreeData(nodes: DepartmentNode[]): any[] {
  return nodes.map((n) => ({ key: n.id, value: n.id, title: n.name, children: toTreeData(n.children) }));
}

function flatten(nodes: DepartmentNode[]): DepartmentNode[] {
  return nodes.flatMap((n) => [n, ...flatten(n.children)]);
}

export default function OrgPage() {
  const has = useHasPermission();
  const [tree, setTree] = useState<DepartmentNode[]>([]);
  const [selectedDept, setSelectedDept] = useState<string | null>(null);
  const [allUsers, setAllUsers] = useState<PortalUser[]>([]);
  const [deptModalOpen, setDeptModalOpen] = useState(false);
  const [editingDept, setEditingDept] = useState<DepartmentNode | null>(null);
  const [positionsOpen, setPositionsOpen] = useState(false);
  const [form] = Form.useForm();

  const reloadTree = useCallback(() => {
    getDepartmentTree().then(setTree);
    listUsers().then(setAllUsers); // 负责人下拉数据源
  }, []);
  useEffect(() => { reloadTree(); }, [reloadTree]);

  const flat = useMemo(() => flatten(tree), [tree]);
  const selected = flat.find((d) => d.id === selectedDept) ?? null;

  const openCreate = () => {
    setEditingDept(null);
    form.resetFields();
    form.setFieldsValue({ parentId: selectedDept ?? tree[0]?.id });
    setDeptModalOpen(true);
  };
  const openEdit = (d: DepartmentNode) => {
    setEditingDept(d);
    form.setFieldsValue({ name: d.name, parentId: d.parentId, managerUserId: d.managerUserId });
    setDeptModalOpen(true);
  };

  const submitDept = async () => {
    const values = await form.validateFields();
    try {
      if (editingDept) await updateDepartment(editingDept.id, values);
      else await createDepartment(values);
      message.success('保存成功');
      setDeptModalOpen(false);
      reloadTree();
    } catch (e: any) {
      message.error(e.response?.data?.message ?? '保存失败');
    }
  };

  const removeDept = async (d: DepartmentNode) => {
    try {
      await deleteDepartment(d.id);
      message.success('已删除');
      if (selectedDept === d.id) setSelectedDept(null);
      reloadTree();
    } catch (e: any) {
      message.error(e.response?.data?.message ?? '删除失败');
    }
  };

  const isRoot = editingDept?.parentId === null;
  return (
    <Row gutter={16}>
      <Col span={7}>
        <Card size="small" title="部门"
          extra={has('ORG_MANAGE') && (
            <Space>
              <Button size="small" onClick={() => setPositionsOpen(true)}>岗位管理</Button>
              <Button size="small" type="primary" onClick={openCreate}>新增子部门</Button>
            </Space>
          )}>
          <Tree treeData={toTreeData(tree)} selectedKeys={selectedDept ? [selectedDept] : []}
            defaultExpandAll
            onSelect={(keys) => setSelectedDept((keys[0] as string) ?? null)} />
          {selected && (
            <Space style={{ marginTop: 12 }}>
              <span>{selected.name}{selected.managerName ? `(负责人:${selected.managerName})` : ''}</span>
              {has('ORG_MANAGE') && <Button size="small" type="link" onClick={() => openEdit(selected)}>编辑</Button>}
              {has('ORG_MANAGE') && selected.parentId !== null && (
                <Popconfirm title="确认删除该部门?" onConfirm={() => removeDept(selected)}>
                  <Button size="small" type="link" danger>删除</Button>
                </Popconfirm>
              )}
            </Space>
          )}
        </Card>
      </Col>
      <Col span={17}>
        <UserPanel departmentId={selectedDept} departments={flat} onUsersChanged={reloadTree} />
      </Col>

      <Modal title={editingDept ? '编辑部门' : '新增部门'} open={deptModalOpen}
        onOk={submitDept} onCancel={() => setDeptModalOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, max: 100 }]}>
            <Input />
          </Form.Item>
          {!isRoot && (
            <Form.Item name="parentId" label="上级部门(改动即移动)" rules={[{ required: true }]}>
              <TreeSelect treeData={toTreeData(tree)} treeDefaultExpandAll />
            </Form.Item>
          )}
          <Form.Item name="managerUserId" label="负责人">
            <Select allowClear showSearch optionFilterProp="label"
              options={allUsers.map((u) => ({ value: u.id, label: u.name || u.username }))} />
          </Form.Item>
        </Form>
      </Modal>

      <PositionModal open={positionsOpen} onClose={() => setPositionsOpen(false)} />
    </Row>
  );
}
