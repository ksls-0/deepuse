<template>
  <div class="chat-wrap">
    <SeniorHeader :title="title" back-to="/senior/characters" />

    <div class="chat-body">
      <div ref="scrollBox" class="messages">
        <div v-if="loading" class="s-text">正在加载…</div>

        <div v-else-if="messages.length === 0" class="s-card empty-card">
          <p class="s-h2">你可以先打个招呼</p>
          <p class="s-text">比如问一句「你是谁呀」。</p>
        </div>

        <div
          v-for="msg in messages"
          :key="msg.id"
          :class="['msg', msg.role === 'user' ? 'from-user' : 'from-ai']"
        >
          <div class="msg-role">{{ msg.role === 'user' ? '我' : title }}</div>
          <div class="msg-bubble">
            <span v-if="msg.streaming && !msg.content" class="dots">正在输入…</span>
            <span v-else>{{ msg.content }}</span>
          </div>
        </div>
      </div>

      <p v-if="error" class="s-alert">{{ error }}</p>

      <div class="composer">
        <label class="s-label" for="senior-chat-input">说点什么</label>
        <textarea
          id="senior-chat-input"
          v-model="draft"
          class="s-textarea composer-input"
          rows="3"
          maxlength="4000"
          placeholder="在这里打字，然后点下面的「发送」"
          :disabled="sending"
        ></textarea>
        <button
          class="s-btn s-btn-primary composer-send"
          type="button"
          :disabled="sending || !draft.trim()"
          @click="send"
        >
          {{ sending ? '正在等回复…' : '发 送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import SeniorHeader from '@/components/senior/SeniorHeader.vue';
import { conversationApi } from '@/api/conversations';
import { postSse } from '@/api/sse';

const route = useRoute();
const conversationId = route.params.id;

const title = ref('角色');
const messages = ref([]);
const draft = ref('');
const loading = ref(true);
const sending = ref(false);
const error = ref('');
const scrollBox = ref(null);

function scrollToBottom() {
  nextTick(() => {
    if (scrollBox.value) {
      scrollBox.value.scrollTop = scrollBox.value.scrollHeight;
    }
  });
}

async function load() {
  try {
    const [conversation, page] = await Promise.all([
      conversationApi.detail(conversationId),
      conversationApi.messages(conversationId, { size: 100 })
    ]);
    title.value = conversation.title || conversation.templateName || '角色';
    messages.value = (page.items || []).map((m) => ({ ...m, streaming: false }));
    scrollToBottom();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
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
    streaming: false
  };
  const aiMsg = {
    id: tempId(),
    role: 'assistant',
    content: '',
    streaming: true
  };
  messages.value.push(userMsg, aiMsg);
  scrollToBottom();

  try {
    await postSse(
      `/api/conversations/${conversationId}/chat/stream`,
      { message: text },
      {
        onEvent: (name, data) => {
          if (name === 'delta' && data && data.text) {
            aiMsg.content += data.text;
            scrollToBottom();
          } else if (name === 'done') {
            Object.assign(userMsg, data.userMessage || {});
            Object.assign(aiMsg, data.assistantMessage || {}, { streaming: false });
          } else if (name === 'error') {
            error.value = (data && data.message) || '对方暂时没回应，请再试一次';
            if (!aiMsg.content) aiMsg.content = '（没有收到回应）';
            aiMsg.streaming = false;
          }
        }
      }
    );
  } catch (e) {
    error.value = e.message;
    if (!aiMsg.content) aiMsg.content = '（发送失败，请检查网络后重试）';
  } finally {
    aiMsg.streaming = false;
    sending.value = false;
    scrollToBottom();
  }
}

onMounted(load);
</script>

<style scoped>
.chat-wrap {
  min-height: 100vh;
  background: #fff;
  padding-top: 92px;
  box-sizing: border-box;
}

.chat-body {
  max-width: 760px;
  margin: 0 auto;
  padding: 0 16px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 92px);
  box-sizing: border-box;
}

.messages {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 12px 0;
  overflow-y: auto;
}

.msg {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.msg-role {
  font-size: 17px;
  font-weight: 700;
  color: #333;
}

.msg-bubble {
  font-size: 22px;
  line-height: 1.7;
  padding: 16px 18px;
  border-radius: 16px;
  border: 2px solid #333;
  white-space: pre-wrap;
  word-break: break-word;
}

.from-user {
  align-items: flex-end;
}

.from-user .msg-bubble {
  background: #12306b;
  border-color: #12306b;
  color: #fff;
}

.from-ai .msg-bubble {
  background: #fff;
  color: #111;
}

.empty-card {
  gap: 8px;
}

.composer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 12px;
  border-top: 2px solid #333;
}

.composer-input {
  min-height: 110px;
}

.composer-send {
  min-height: 68px;
  font-size: 24px;
}

.dots {
  color: #333;
}
</style>
