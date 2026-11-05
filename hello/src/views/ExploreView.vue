<template>
  <div class="page">
    <AppNavbar title="广场" :show-back="false" />

    <main class="content">
      <header class="head">
        <div class="tabs">
          <button :class="['tab', { active: tab === 'sets' }]" @click="switchTab('sets')">
            模板广场
          </button>
          <button :class="['tab', { active: tab === 'templates' }]" @click="switchTab('templates')">
            角色广场
          </button>
        </div>

        <div class="filters">
          <input
            v-model.trim="keyword"
            type="search"
            class="search"
            :placeholder="tab === 'sets' ? '搜索模板标题…' : '搜索角色名称…'"
            @keyup.enter="reload"
          />
          <select v-model="sort" class="sort" @change="reload">
            <option value="latest">最新发布</option>
            <option value="hot">{{ tab === 'sets' ? '最多导入' : '最多使用' }}</option>
          </select>
        </div>
      </header>

      <p class="hint">
        <template v-if="tab === 'sets'">模板是可复用的问题清单，导入后即可填写并生成你自己的角色。</template>
        <template v-else>角色是别人已经生成好的角色，可直接拿去对话。</template>
      </p>

      <p v-if="error" class="alert error">{{ error }}</p>

      <div v-if="loading" class="empty">加载中…</div>

      <div v-else-if="items.length === 0" class="empty">这里还空着，换个关键词试试</div>

      <template v-else>
        <div class="grid">
          <template v-if="tab === 'sets'">
            <SetCard
              v-for="item in items"
              :key="`s-${item.id}`"
              :set="item"
              @select="openSet(item)"
            />
          </template>
          <template v-else>
            <TemplateCard
              v-for="item in items"
              :key="`t-${item.id}`"
              :template="item"
              @select="openTemplate(item)"
            />
          </template>
        </div>

        <div class="more">
          <button v-if="hasNext" class="ghost-btn" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </button>
          <span v-else class="end">— 没有更多了 —</span>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppNavbar from '@/components/layout/AppNavbar.vue';
import SetCard from '@/components/SetCard.vue';
import TemplateCard from '@/components/TemplateCard.vue';
import { setApi } from '@/api/sets';
import { templateApi } from '@/api/templates';

const router = useRouter();

const tab = ref('sets');
const keyword = ref('');
const sort = ref('latest');

const items = ref([]);
const page = ref(0);
const hasNext = ref(false);
const loading = ref(true);
const loadingMore = ref(false);
const error = ref('');

async function fetchPage(reset) {
  error.value = '';
  if (reset) {
    page.value = 0;
    loading.value = true;
  } else {
    loadingMore.value = true;
  }

  try {
    const params = { keyword: keyword.value, sort: sort.value, page: page.value, size: 12 };
    const result = tab.value === 'sets'
      ? await setApi.exploreSets(params)
      : await templateApi.explore(params);

    items.value = reset ? result.items : [...items.value, ...result.items];
    hasNext.value = result.hasNext;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
}

function reload() {
  fetchPage(true);
}

function switchTab(next) {
  if (tab.value === next) return;
  tab.value = next;
  items.value = [];
  fetchPage(true);
}

function loadMore() {
  page.value += 1;
  fetchPage(false);
}

function openSet(item) {
  router.push(`/explore/sets/${item.id}`);
}

function openTemplate(item) {
  router.push(`/explore/templates/${item.id}`);
}

onMounted(() => fetchPage(true));
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
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.tabs {
  display: flex;
  gap: 6px;
  background: #eef2f7;
  border-radius: 16px;
  padding: 4px;
}

.tab {
  border: none;
  background: transparent;
  padding: 9px 22px;
  border-radius: 12px;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.tab.active {
  background: #fff;
  color: #1f1f1f;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.filters { display: flex; gap: 10px; }

.search {
  width: 220px;
  padding: 9px 13px;
  border: 2px solid #e2e2e2;
  border-radius: 12px;
  font-size: 13px;
  outline: none;
  background: #fff;
}

.search:focus { border-color: #54a3f8; }

.sort {
  padding: 9px 12px;
  border: 2px solid #e2e2e2;
  border-radius: 12px;
  font-size: 13px;
  outline: none;
  background: #fff;
}

.hint {
  font-size: 13px;
  color: #8a8a8a;
  margin-bottom: 20px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.more {
  text-align: center;
  margin-top: 28px;
}

.end { font-size: 12px; color: #bbb; }

.ghost-btn {
  background: #fff;
  border: 2px solid #e2e2e2;
  border-radius: 14px;
  padding: 9px 26px;
  font-size: 14px;
  color: #555;
  cursor: pointer;
}

.ghost-btn:hover:not(:disabled) { border-color: #54a3f8; color: #54a3f8; }

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
}
</style>
