<template>
  <div class="page">
    <AppNavbar title="我的模板" :show-back="false" />

    <main class="content">
      <header class="head">
        <div>
          <h1>我的模板</h1>
          <p class="hint">共 {{ total }} 个 · 点击卡片去填写答卷</p>
        </div>
        <router-link to="/sets/new" class="primary-btn">＋ 新建模板</router-link>
      </header>

      <p v-if="error" class="alert error">{{ error }}</p>

      <div v-if="loading" class="empty">加载中…</div>

      <div v-else-if="sets.length === 0" class="empty">
        <p>还没有模板</p>
        <router-link to="/sets/new" class="primary-btn">创建第一个模板</router-link>
      </div>

      <div v-else class="grid">
        <div v-for="set in sets" :key="set.id" class="cell">
          <SetCard :set="set" @select="goFill(set)" />

          <div class="actions">
            <button class="mini" @click="goEdit(set)">编辑</button>
            <button class="mini" @click="toggleVisibility(set)">
              {{ set.visibility === 1 ? '设为私密' : '公开' }}
            </button>
            <button class="mini danger" @click="remove(set)">删除</button>
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
import SetCard from '@/components/SetCard.vue';
import { setApi } from '@/api/sets';

const router = useRouter();

const sets = ref([]);
const total = ref(0);
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const page = await setApi.mine(0, 50);
    sets.value = page.items || [];
    total.value = page.total || 0;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function goFill(set) {
  router.push(`/sets/${set.id}/fill`);
}

function goEdit(set) {
  router.push(`/sets/${set.id}/edit`);
}

async function toggleVisibility(set) {
  const next = set.visibility === 1 ? 0 : 1;
  try {
    await setApi.setVisibility(set.id, next);
    set.visibility = next;
  } catch (e) {
    error.value = e.message;
  }
}

async function remove(set) {
  if (!confirm(`确定删除模板「${set.title}」吗？已生成的角色不受影响。`)) return;
  try {
    await setApi.remove(set.id);
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
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.head h1 { font-size: 24px; color: #1f1f1f; margin-bottom: 6px; }
.hint { font-size: 13px; color: #8a8a8a; }

.primary-btn {
  display: inline-block;
  background: #54a3f8;
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 10px 20px;
  font-size: 14px;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.2s;
}

.primary-btn:hover { background: #3d8be6; }

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.cell { display: flex; flex-direction: column; gap: 8px; }

.actions {
  display: flex;
  gap: 8px;
  padding: 0 4px;
}

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
  gap: 16px;
}
</style>
