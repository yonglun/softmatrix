import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Input, Button, List, Typography, Space } from 'antd';
import { streamChat } from '../api/chat';

interface Msg { role: 'user' | 'assistant'; text: string; error?: boolean; }

export default function ChatPage() {
  const { id } = useParams<{ id: string }>();
  const sessionId = useMemo(() => crypto.randomUUID(), []);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);

  const send = async () => {
    if (!input.trim() || !id) return;
    const question = input;
    setInput('');
    setMessages((m) => [...m, { role: 'user', text: question }, { role: 'assistant', text: '' }]);
    setSending(true);

    const appendToAssistant = (fn: (prev: Msg) => Msg) =>
      setMessages((m) => {
        const copy = [...m];
        copy[copy.length - 1] = fn(copy[copy.length - 1]);
        return copy;
      });

    await streamChat(id, sessionId, question, {
      onToken: (t) => appendToAssistant((prev) => ({ ...prev, text: prev.text + t })),
      onError: (msg) => appendToAssistant((prev) => ({ ...prev, text: prev.text + `\n[${msg}]`, error: true })),
      onDone: () => setSending(false),
    });
    setSending(false);
  };

  return (
    <div style={{ maxWidth: 720, margin: '0 auto' }}>
      <List
        dataSource={messages}
        renderItem={(m) => (
          <List.Item>
            <Typography.Text type={m.error ? 'danger' : m.role === 'user' ? 'secondary' : undefined}>
              <b>{m.role === 'user' ? '我' : 'Agent'}:</b> {m.text}
            </Typography.Text>
          </List.Item>
        )}
      />
      <Space.Compact style={{ width: '100%', marginTop: 16 }}>
        <Input value={input} onChange={(e) => setInput(e.target.value)}
          onPressEnter={send} disabled={sending} placeholder="输入消息..." />
        <Button type="primary" onClick={send} loading={sending}>发送</Button>
      </Space.Compact>
    </div>
  );
}
