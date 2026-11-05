<template>
  <div class="page">
    <AppNavbar :title="template.name || '角色详情'" back-to="/explore" />

    <main class="content">
      <p v-if="error" class="alert error">{{ error }}</p>
      <div v-if="loading" class="empty">加载中…</div>

      <template v-else-if="template.id">
        <CharacterCard :template="template" />

        <p class="meta">
          by {{ template.ownerAccount || '匿名' }}
          <template v-if="template.setTitle"> · 来自模板《{{ template.setTitle }}》</template>
          · 被使用 {{ template.useCount }} 次
        </p>

        <footer class="footer">
          <template v-if="template.mine">
            <p class="tip">这是你自己的角色。</p>
            <router-link :to="`/templates/${template.id}`" class="ghost-btn">去管理</router-link>
          </template>
          <template v-else>
            <p class="tip">开始对话会新建一个只属于你的会话。</p>
            <div class="action-group">
              <button class="report-link" type="button" @click="openReport">举报</button>
              <button class="primary-btn" :disabled="starting" @click="startChat">
                {{ starting ? '创建会话中…' : '使用此角色对话' }}
              </button>
            </div>
          </template>
        </footer>
      </template>
      <p v-if="reportNotice" class="alert ok">{{ reportNotice }}</p>
    </main>

    <ReportDialog
      :visible="showReport"
      target-type="TEMPLATE"
      :target-id="template.id"
      @close="showReport = false"
      @done="reportNotice = '举报已提交，我们会尽快处理'"
    />
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppNavbar from '@/components/layout/AppNavbar.vue';
import CharacterCard from '@/components/CharacterCard.vue';
import { templateApi } from '@/api/templates';
import { conversationApi } from '@/api/conversations';
import ReportDialog from '@/components/ReportDialog.vue';

const route = useRoute();
const router = useRouter();

const template = reactive({ id: null });
const loading = ref(true);
const starting = ref(false);
const error = ref('');
const showReport = ref(false);
const reportNotice = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const data = await templateApi.exploreDetail(route.params.id);
    Object.assign(template, data);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function openReport() {
  if (!localStorage.getItem('token')) {
    router.push({ path: '/login', query: { redirect: route.fullPath } });
    return;
  }
  showReport.value = true;
}

async function startChat() {
  if (!localStorage.getItem('token')) {
    router.push({ path: '/login', query: { redirect: route.fullPath } });
    return;
  }
  starting.value = true;
  error.value = '';
  try {
    const conversation = await conversationApi.create(template.id);
    router.push(`/chat/${conversation.id}`);
  } catch (e) {
    error.value = e.message;
  } finally {
    starting.value = false;
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
  max-width: 720px;
  margin: 0 auto;
  padding: 32px 20px 60px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.meta {
  font-size: 12px;
  color: #9a9a9a;
  padding-left: 4px;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.tip { font-size: 13px; color: #8a8a8a; }

.primary-btn {
  background: #54a3f8;
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 11px 26px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}

.primary-btn:hover:not(:disabled) { background: #3d8be6; }
.primary-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.ghost-btn {
  background: #fff;
  border: 2px solid #e2e2e2;
  border-radius: 14px;
  padding: 9px 20px;
  font-size: 14px;
  color: #555;
  text-decoration: none;
}

.ghost-btn:hover { border-color: #54a3f8; color: #54a3f8; }

.alert {
  font-size: 13px;
  border-radius: 10px;
  padding: 10px 14px;
}

.alert.error { color: #c62828; background: #ffebee; }

.empty { text-align: center; color: #999; font-size: 14px; padding: 60px 0; }
.action-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.report-link {
  background: none;
  border: none;
  color: #9a9a9a;
  font-size: 13px;
  cursor: pointer;
  text-decoration: underline;
}

.report-link:hover { color: #ff4d4f; }

.alert.ok { color: #2e7d32; background: #e8f5e9; }
</style>
