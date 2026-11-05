<template>
  <div class="chat-page">
    <AppNavbar :title="title" back-to="/conversations">
      <template #center>
        <span class="nav-title">{{ title }}</span>
      </template>
    </AppNavbar>

    <div class="chat-main">
      <p v-if="error" class="alert error">{{ error }}</p>

      <div ref="scrollBox" class="messages">
        <div v-if="loading" class="hint">加载中…</div>

        <div v-else-if="messages.length === 0" class="empty">
          <div class="empty-icon">💬</div>
          <p>开始和「{{ title }}」对话吧</p>
          <p class="empty-sub">角色会记住这段对话的上下文</p>
        </div>

        <div
          v-for="msg in messages"
          :key="msg.id"
          :class="['message', msg.role === 'user' ? 'user' : 'ai']"
        >
          <div class="avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
          <div class="bubble-wrap">
            <div v-if="msg.streaming && !msg.content" class="bubble typing">
              <span></span><span></span><span></span>
            </div>
            <div v-else class="bubble">
              {{ msg.content }}<span v-if="msg.streaming" class="caret"></span>
            </div>
            <div v-if="!msg.streaming" class="time">{{ formatTime(msg.createdAt) }}</div>
          </div>
        </div>
      </div>

      <div class="input-area">
        <textarea
          v-model="draft"
          rows="1"
          class="input"
          placeholder="输入消息，Enter 发送 / Shift+Enter 换行"
          :disabled="sending"
          @keydown.enter.exact.prevent="send"
        ></textarea>
        <button class="send-btn" :disabled="sending || !draft.trim()" @click="send">
          {{ sending ? '…' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import AppNavbar from '@/components/layout/AppNavbar.vue';
import { conversationApi } from '@/api/conversations';
import { postSse } from '@/api/sse';

const route = useRoute();

const title = ref('AI 对话');
const messages = ref([]);
const draft = ref('');
const loading = ref(true);
const sending = ref(false);
const error = ref('');
const scrollBox = ref(null);

let lastScrollAt = 0;

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [conversation, page] = await Promise.all([
      conversationApi.detail(route.params.id),
      conversationApi.messages(route.params.id, { size: 100 })
    ]);
    title.value = conversation.title || conversation.templateName || 'AI 对话';
    messages.value = (page.items || []).map((m) => ({ ...m, streaming: false }));
    scrollToBottom();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function scrollToBottom(force = true) {
  // 流式增量很密集，节流一下避免每段都触发滚动
  const now = Date.now();
  if (!force && now - lastScrollAt < 80) return;
  lastScrollAt = now;

  nextTick(() => {
    if (scrollBox.value) {
      scrollBox.value.scrollTop = scrollBox.value.scrollHeight;
    }
  });
}

function formatTime(value) {
  if (!value) return '';
  return new Date(value).toLocaleTimeString();
}

function tempId() {
  return `tmp-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

async function send() {
  const text = draft.value.trim();
  if (!text || sending.value) return;

  error.value = '';
  sending.value = true;
  draft.value = '';

  const userMsg = {
    id: tempId(),
    role: 'user',
    content: text,
    createdAt: new Date().toISOString(),
    streaming: false
  };
  const assistantMsg = {
    id: tempId(),
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
    streaming: true
  };

  messages.value.push(userMsg, assistantMsg);
  scrollToBottom();

  try {
    await postSse(
      `/api/conversations/${route.params.id}/chat/stream`,
      { message: text },
      {
        onEvent: (name, data) => {
          if (name === 'user') {
            Object.assign(userMsg, data);
          } else if (name === 'delta' && data && data.text) {
            assistantMsg.content += data.text;
            scrollToBottom(false);
          } else if (name === 'done') {
            Object.assign(userMsg, data.userMessage || {});
            Object.assign(assistantMsg, data.assistantMessage || {}, { streaming: false });
          } else if (name === 'error') {
            const message = (data && data.message) || 'AI 服务出错';
            error.value = message;
            if (!assistantMsg.content) assistantMsg.content = `发送失败：${message}`;
            assistantMsg.streaming = false;
          }
        }
      }
    );
  } catch (e) {
    error.value = e.message;
    if (!assistantMsg.content) {
      assistantMsg.content = `发送失败：${e.message}`;
    }
  } finally {
    assistantMsg.streaming = false;
    sending.value = false;
    scrollToBottom();
  }
}

onMounted(load);
</script>

<style scoped>
.chat-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f3f6fa 0%, #ffffff 40%);
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
  padding: 76px 20px 20px;
  box-sizing: border-box;
  min-height: 100vh;
}

.nav-title { font-size: 16px; color: #1f1f1f; font-weight: 600; }

.messages {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 10px 0 20px;
}

.hint { text-align: center; color: #999; font-size: 14px; padding: 40px 0; }

.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
  gap: 8px;
}

.empty-icon { font-size: 44px; }
.empty-sub { font-size: 13px; color: #b5b5b5; }

.message { display: flex; gap: 10px; }
.message.user { flex-direction: row-reverse; }

.avatar {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.bubble-wrap {
  display: flex;
  flex-direction: column;
  max-width: 72%;
}

.message.user .bubble-wrap { align-items: flex-end; }

.bubble {
  padding: 11px 15px;
  border-radius: 16px;
  background: #fff;
  color: #2a2a2a;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.message.user .bubble {
  background: #54a3f8;
  color: #fff;
}

.caret {
  display: inline-block;
  width: 7px;
  height: 15px;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: #54a3f8;
  animation: blink 1s steps(2, start) infinite;
}

@keyframes blink {
  to { visibility: hidden; }
}

.time {
  font-size: 11px;
  color: #b5b5b5;
  margin-top: 4px;
}

.typing { display: flex; gap: 4px; }

.typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #bbb;
  animation: typing 1.3s infinite;
}

.typing span:nth-child(2) { animation-delay: 0.18s; }
.typing span:nth-child(3) { animation-delay: 0.36s; }

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.45; }
  30% { transform: translateY(-5px); opacity: 1; }
}

.input-area {
  position: sticky;
  bottom: 0;
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 14px 0;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(8px);
  border-top: 1px solid #ececec;
}

.input {
  flex: 1;
  padding: 12px 16px;
  border: 2px solid #e2e2e2;
  border-radius: 20px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.6;
  outline: none;
  resize: none;
  max-height: 120px;
  transition: border-color 0.2s;
}

.input:focus { border-color: #54a3f8; }

.send-btn {
  flex-shrink: 0;
  padding: 12px 24px;
  background: #54a3f8;
  color: #fff;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}

.send-btn:hover:not(:disabled) { background: #3d8be6; }
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.alert {
  font-size: 13px;
  border-radius: 10px;
  padding: 10px 14px;
  margin-bottom: 12px;
}

.alert.error { color: #c62828; background: #ffebee; }
</style>
