export interface ChatCallbacks {
  onToken: (text: string) => void;
  onError: (message: string) => void;
  onDone: () => void;
}

export async function streamChat(
  agentId: string,
  sessionId: string,
  message: string,
  cb: ChatCallbacks
): Promise<void> {
  let res: Response;
  try {
    res = await fetch(`/api/agents/${agentId}/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ sessionId, message }),
    });
  } catch {
    cb.onError('网络错误,请重试');
    return;
  }

  if (res.status === 401) {
    window.location.href = '/oauth2/authorization/keycloak';
    return;
  }
  if (!res.ok || !res.body) {
    cb.onError('运行失败,请重试');
    return;
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    // SSE 帧以空行分隔;取每帧的 data: 内容
    const frames = buffer.split('\n\n');
    buffer = frames.pop() ?? '';
    for (const frame of frames) {
      for (const line of frame.split('\n')) {
        if (line.startsWith('data:')) {
          cb.onToken(line.slice(5).trimStart());
        }
      }
    }
  }
  cb.onDone();
}
