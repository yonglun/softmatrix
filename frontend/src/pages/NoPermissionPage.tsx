import { Button, Result } from 'antd';
import { logout } from '../api/client';

/** JIT 落库但尚未被分配任何角色的用户看到的整页提示。 */
export default function NoPermissionPage() {
  return (
    <Result status="info" title="暂无权限"
      subTitle="您的账号尚未被分配任何角色,请联系管理员开通。"
      extra={<Button onClick={logout}>登出</Button>} />
  );
}
