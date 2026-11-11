<template>
  <div class="s-page">
    <SeniorHeader title="角色工坊" />

    <div class="s-content">
      <h1 class="s-h1">您好！想做什么？</h1>

      <button class="s-card s-card-clickable tile" type="button" @click="router.push('/senior/characters')">
        <span class="tile-icon">💬</span>
        <span class="tile-body">
          <span class="tile-title">和角色说话</span>
          <span class="tile-sub">{{ counts.templates }} 个角色可以聊天</span>
        </span>
        <span class="tile-arrow">›</span>
      </button>

      <button class="s-card s-card-clickable tile" type="button" @click="router.push('/senior/pick')">
        <span class="tile-icon">➕</span>
        <span class="tile-body">
          <span class="tile-title">做一个新角色</span>
          <span class="tile-sub">照着问题慢慢答，AI 帮你写成角色</span>
        </span>
        <span class="tile-arrow">›</span>
      </button>

      <button class="s-card s-card-clickable tile" type="button" @click="router.push('/senior/help')">
        <span class="tile-icon">❓</span>
        <span class="tile-body">
          <span class="tile-title">帮助 / 退出</span>
          <span class="tile-sub">怎么用、怎么退出登录</span>
        </span>
        <span class="tile-arrow">›</span>
      </button>

      <p v-if="error" class="s-alert">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import SeniorHeader from '@/components/senior/SeniorHeader.vue';
import { setApi } from '@/api/sets';
import { templateApi } from '@/api/templates';

const router = useRouter();
const counts = reactive({ templates: 0, sets: 0 });
const error = ref('');

async function load() {
  error.value = '';
  try {
    const [tplPage, setPage] = await Promise.all([
      templateApi.mine(0, 1),
      setApi.mine(0, 1)
    ]);
    counts.templates = tplPage.total || 0;
    counts.sets = setPage.total || 0;
  } catch (e) {
    error.value = e.message;
  }
}

onMounted(load);
</script>

<style scoped>
.tile {
  flex-direction: row;
  align-items: center;
  gap: 18px;
  padding: 24px 20px;
}

.tile-icon {
  font-size: 40px;
  line-height: 1;
  flex: 0 0 auto;
}

.tile-body {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.tile-title {
  font-size: 26px;
  font-weight: 700;
  color: #000;
}

.tile-sub {
  font-size: 18px;
  color: #333;
}

.tile-arrow {
  flex: 0 0 auto;
  font-size: 34px;
  color: #12306b;
  line-height: 1;
}
</style>
