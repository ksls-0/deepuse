<template>
  <div class="page">
    <AppNavbar title="我的对话" :show-back="false" />

    <main class="content">
      <header class="head">
        <h1>我的对话</h1>
        <p class="hint">共 {{ total }} 个会话</p>
      </header>

      <p v-if="error" class="alert error">{{ error }}</p>

      <div v-if="loading" class="empty">加载中…</div>

      <div v-else-if="conversations.length === 0" class="empty">
        <p>还没有对话</p>
        <p class="sub">从我的模板或模板广场里挑一个角色开始聊</p>
        <div class="empty-actions">
          <router-link to="/templates" class="primary-btn">我的模板</router-link>
          <router-link to="/explore" class="ghost-btn">去广场</router-link>
        </div>
      </div>

      <div v-else class="list">
        <router-link
          v-for="c in conversations"
          :key="c.id"
          :to="`/chat/${c.id}`"
          class="item"
        >
          <div class="avatar">{{ (c.templateName || '?').charAt(0).toUpperCase() }}</div>
          <div class="info">
            <div class="title">{{ c.title || c.templateName }}</div>
            <div class="sub">角色：{{ c.templateName }} · {{ formatTime(c.updatedAt) }}</div>
          </div>
          <button class="del" title="删除会话" @click.prevent.stop="remove(c)">✕</button>
        </router-link>
      </div>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import AppNavbar from '@/components/layout/AppNavbar.vue';
import { conversationApi } from '@/api/conversations';

const conversations = ref([]);
const total = ref(0);
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const page = await conversationApi.mine(0, 50);
    conversations.value = page.items || [];
    total.value = page.total || 0;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function remove(c) {
  if (!confirm('确定删除这个会话吗？历史消息会一并隐藏。')) return;
  try {
    await conversationApi.remove(c.id);
    await load();
  } catch (e) {
    error.value = e.message;
  }
}

function formatTime(value) {
  if (!value) return '';
  return new Date(value).toLocaleString();
}

onMounted(load);
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f3f6fa 0%, #ffffff 45%);
  padding-top: 60px;
  box-sizing: border-box;
}

.content {
  max-width: 720px;
  margin: 0 auto;
  padding: 32px 20px 60px;
}

.head { margin-bottom: 22px; }
.head h1 { font-size: 24px; color: #1f1f1f; margin-bottom: 6px; }
.hint { font-size: 13px; color: #8a8a8a; }

.list { display: flex; flex-direction: column; gap: 10px; }

.item {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border: 2px solid #ececec;
  border-radius: 16px;
  padding: 14px 18px;
  text-decoration: none;
  transition: all 0.2s;
}

.item:hover { border-color: #54a3f8; }

.avatar {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, #54a3f8, #a7cdfa);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.info { flex: 1; min-width: 0; }

.title {
  font-size: 15px;
  color: #1f1f1f;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub { font-size: 12px; color: #9a9a9a; }

.del {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border: 1px solid #e2e2e2;
  background: #fff;
  border-radius: 50%;
  color: #999;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.del:hover { border-color: #ff4d4f; color: #ff4d4f; }

.primary-btn {
  display: inline-block;
  background: #54a3f8;
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 10px 22px;
  font-size: 14px;
  text-decoration: none;
}

.ghost-btn {
  display: inline-block;
  background: #fff;
  border: 2px solid #e2e2e2;
  border-radius: 14px;
  padding: 8px 20px;
  font-size: 14px;
  color: #555;
  text-decoration: none;
}

.alert {
  font-size: 13px;
  border-radius: 10px;
  padding: 10px 14px;
  margin-bottom: 16px;
}

.alert.error { color: #c62828; background: #ffebee; }

.empty {
  text-align: center;
  color: #999;
  font-size: 14px;
  padding: 60px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.empty .sub { font-size: 13px; color: #b5b5b5; }
.empty-actions { display: flex; gap: 10px; margin-top: 6px; }
</style>
