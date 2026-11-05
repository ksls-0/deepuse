<template>
  <div class="page">
    <AppNavbar title="我的角色" :show-back="false" />

    <main class="content">
      <header class="head">
        <div>
          <h1>我的角色</h1>
          <p class="hint">共 {{ total }} 个 · 公开后可出现在角色广场供他人使用</p>
        </div>
      </header>

      <p v-if="error" class="alert error">{{ error }}</p>

      <div v-if="loading" class="empty">加载中…</div>

      <div v-else-if="templates.length === 0" class="empty">
        <p>还没有角色</p>
        <p class="sub">先去模板填写答卷，再点「生成角色」</p>
        <router-link to="/sets" class="primary-btn">去我的模板</router-link>
      </div>

      <div v-else class="grid">
        <div v-for="tpl in templates" :key="tpl.id" class="cell">
          <TemplateCard :template="tpl" @select="openDetail(tpl)" />

          <div class="actions">
            <button class="mini" @click="startChat(tpl)">对话</button>
            <button class="mini" @click="toggleVisibility(tpl)">
              {{ tpl.visibility === 1 ? '设为私密' : '公开' }}
            </button>
            <button class="mini danger" @click="remove(tpl)">删除</button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppNavbar from '@/components/layout/AppNavbar.vue';
import TemplateCard from '@/components/TemplateCard.vue';
import { templateApi } from '@/api/templates';
import { conversationApi } from '@/api/conversations';

const router = useRouter();

const templates = ref([]);
const total = ref(0);
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const page = await templateApi.mine(0, 50);
    templates.value = page.items || [];
    total.value = page.total || 0;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function openDetail(tpl) {
  router.push(`/templates/${tpl.id}`);
}

async function startChat(tpl) {
  try {
    const conversation = await conversationApi.create(tpl.id);
    router.push(`/chat/${conversation.id}`);
  } catch (e) {
    error.value = e.message;
  }
}

async function toggleVisibility(tpl) {
  const next = tpl.visibility === 1 ? 0 : 1;
  try {
    await templateApi.setVisibility(tpl.id, next);
    tpl.visibility = next;
  } catch (e) {
    error.value = e.message;
  }
}

async function remove(tpl) {
  if (!confirm(`确定删除角色「${tpl.name}」吗？`)) return;
  try {
    await templateApi.remove(tpl.id);
    await load();
  } catch (e) {
    error.value = e.message;
  }
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
  max-width: 1040px;
  margin: 0 auto;
  padding: 32px 20px 60px;
}

.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 24px;
}

.head h1 { font-size: 24px; color: #1f1f1f; margin-bottom: 6px; }
.hint { font-size: 13px; color: #8a8a8a; }

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.cell { display: flex; flex-direction: column; gap: 8px; }

.actions { display: flex; gap: 8px; padding: 0 4px; }

.mini {
  flex: 1;
  background: #fff;
  border: 1px solid #e2e2e2;
  border-radius: 10px;
  padding: 7px 0;
  font-size: 12px;
  color: #555;
  cursor: pointer;
  transition: all 0.2s;
}

.mini:hover { border-color: #54a3f8; color: #54a3f8; }
.mini.danger:hover { border-color: #ff4d4f; color: #ff4d4f; }

.primary-btn {
  display: inline-block;
  background: #54a3f8;
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 10px 22px;
  font-size: 14px;
  text-decoration: none;
  cursor: pointer;
}

.primary-btn:hover { background: #3d8be6; }

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
</style>
