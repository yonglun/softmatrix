import { useCallback, useEffect, useState } from 'react';
import { Button, Input, List, Modal, Popconfirm, Space, message } from 'antd';
import type { Position } from '../../api/types';
import { createPosition, deletePosition, listPositions } from '../../api/org';

export default function PositionModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [positions, setPositions] = useState<Position[]>([]);
  const [name, setName] = useState('');

  const reload = useCallback(() => { listPositions().then(setPositions); }, []);
  useEffect(() => { if (open) reload(); }, [open, reload]);

  const add = async () => {
    if (!name.trim()) return;
    try {
      await createPosition(name.trim());
      setName('');
      reload();
    } catch (e: any) {
      message.error(e.response?.data?.message ?? '新增失败');
    }
  };

  const remove = async (id: string) => {
    try { await deletePosition(id); reload(); }
    catch (e: any) { message.error(e.response?.data?.message ?? '删除失败'); }
  };

  return (
    <Modal title="岗位管理" open={open} onCancel={onClose} footer={null} destroyOnClose>
      <Space.Compact style={{ width: '100%', marginBottom: 12 }}>
        <Input placeholder="岗位名称" value={name} maxLength={50}
          onChange={(e) => setName(e.target.value)} onPressEnter={add} />
        <Button type="primary" onClick={add}>新增</Button>
      </Space.Compact>
      <List size="small" dataSource={positions}
        renderItem={(p) => (
          <List.Item actions={[
            <Popconfirm key="del" title="确认删除?" onConfirm={() => remove(p.id)}>
              <Button size="small" type="link" danger>删除</Button>
            </Popconfirm>,
          ]}>{p.name}</List.Item>
        )} />
    </Modal>
  );
}
