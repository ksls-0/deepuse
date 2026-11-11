/**
 * 极简 SSE 客户端。
 * EventSource 只支持 GET，无法携带请求体和 Authorization 头，
 * 所以这里用 fetch + ReadableStream 自己解析 text/event-stream。
 */
const BASE = process.env.VUE_APP_API_BASE_URL || '';
import { DEMO_MODE } from '@/demo';

export async function postSse(path, body, { onEvent, signal } = {}) {
  const token = localStorage.getItem('token');
  const response = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(body),
    signal
  });

  if (response.status === 401) {
    localStorage.removeItem('token');
    localStorage.removeItem('userId');
    localStorage.removeItem('account');
    localStorage.removeItem('role');
    if (DEMO_MODE) {
      // 演示版：回首页自动重新静默登录，10 秒内只刷一次防死循环
      const last = Number(sessionStorage.getItem('demoReloginAt') || 0);
      if (Date.now() - last > 10000) {
        sessionStorage.setItem('demoReloginAt', String(Date.now()));
        window.location.replace('/');
      }
    } else {
      window.location.href = '/login';
    }
    throw new Error('登录已过期');
  }

  if (!response.ok || !response.body) {
    let message = `请求失败（HTTP ${response.status}）`;
    try {
      const data = await response.json();
      if (data && data.message) message = data.message;
    } catch (e) {
      // 响应不是 JSON，用默认提示
    }
    throw new Error(message);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        const raw = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const event = parseBlock(raw);
        if (event) onEvent?.(event.name, event.data);
        boundary = buffer.indexOf('\n\n');
      }
    }
  } finally {
    reader.releaseLock?.();
  }
}

function parseBlock(raw) {
  let name = 'message';
  const dataLines = [];

  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) {
      name = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''));
    }
  }

  if (dataLines.length === 0) return null;

  const text = dataLines.join('\n');
  try {
    return { name, data: JSON.parse(text) };
  } catch (e) {
    return { name, data: text };
  }
}
