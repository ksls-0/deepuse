<template>
  <div class="page">
    <AppNavbar :title="detail.title || '题集详情'" back-to="/explore" />

    <main class="content">
      <p v-if="error" class="alert error">{{ error }}</p>
      <div v-if="loading" class="empty">加载中…</div>

      <template v-else-if="detail.id">
        <section class="card">
          <div class="head">
            <div>
              <h1>{{ detail.title }}</h1>
              <p class="meta">
                by {{ detail.ownerAccount || '匿名' }} ·
                {{ detail.questionCount }} 个问题 ·
                被导入 {{ detail.importCount }}
              </p>
            </div>
            <span v-if="detail.visibility === 1" class="badge public">公开</span>
            <span v-else class="badge private">私密</span>
          </div>

          <p v-if="detail.description" class="desc">{{ detail.description }}</p>
        </section>

        <section class="card">
          <h2 class="section-title">问题清单</h2>
          <ol class="q-list">
            <li v-for="q in detail.questions" :key="q.id">{{ q.content }}</li>
          </ol>
        </section>

        <footer class="footer">
          <template v-if="detail.mine">
            <p class="mine-tip">这是你自己的题集。</p>
            <router-link :to="`/sets/${detail.id}/fill`" class="primary-btn">去填写答卷</router-link>
          </template>
          <template v-else>
            <p class="mine-tip">导入后会在你的名下创建一份可自由编辑的副本。</p>
            <button class="primary-btn" :disabled="importing" @click="doImport">
              {{ importing ? '导入中…' : '一键导入' }}
            </button>
          </template>
        </footer>
      </template>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppNavbar from '@/components/layout/AppNavbar.vue';
import { setApi } from '@/api/sets';

const route = useRoute();
const router = useRouter();

const detail = reactive({ id: null });
const loading = ref(true);
const importing = ref(false);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const data = await setApi.exploreSetDetail(route.params.id);
    Object.assign(detail, data);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function doImport() {
  if (!localStorage.getItem('token')) {
    router.push({ path: '/login', query: { redirect: route.fullPath } });
    return;
  }

  importing.value = true;
  error.value = '';
  try {
    const created = await setApi.importSet(detail.id);
    router.push(`/sets/${created.id}/fill`);
  } catch (e) {
    error.value = e.message;
  } finally {
    importing.value = false;
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
  gap: 16px;
}

.card {
  background: #fff;
  border: 2px solid #ececec;
  border-radius: 20px;
  padding: 24px;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.head h1 { font-size: 22px; color: #1f1f1f; margin-bottom: 8px; }

.meta { font-size: 13px; color: #8a8a8a; }

.badge {
  flex-shrink: 0;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  border: 1px solid currentColor;
}

.badge.public { color: #2e7d32; }
.badge.private { color: #8a8a8a; }

.desc {
  margin-top: 16px;
  font-size: 14px;
  color: #555;
  line-height: 1.7;
  white-space: pre-wrap;
}

.section-title { font-size: 16px; color: #1f1f1f; margin-bottom: 14px; }

.q-list {
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.q-list li {
  font-size: 14px;
  color: #2a2a2a;
  line-height: 1.6;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.mine-tip { font-size: 13px; color: #8a8a8a; }

.primary-btn {
  background: #54a3f8;
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 11px 26px;
  font-size: 14px;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.2s;
}

.primary-btn:hover:not(:disabled) { background: #3d8be6; }
.primary-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.alert {
  font-size: 13px;
  border-radius: 10px;
  padding: 10px 14px;
}

.alert.error { color: #c62828; background: #ffebee; }

.empty { text-align: center; color: #999; font-size: 14px; padding: 60px 0; }
</style>
