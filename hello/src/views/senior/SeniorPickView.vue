<template>
  <div class="s-page">
    <SeniorHeader title="选一个模板" back-to="/senior" />

    <div class="s-content">
      <p class="s-text">模板就是一组问题。选一个，接下来照着问题慢慢答就行。</p>

      <div v-if="loading" class="s-text">正在加载…</div>
      <div v-else-if="error" class="s-alert">{{ error }}</div>

      <template v-else-if="list.length === 0">
        <div class="s-card">
          <p class="s-h2">还没有模板</p>
          <p class="s-text">模板需要自己写一组问题，步骤比较多，建议请家里人帮你在普通版里建一个。</p>
        </div>
        <button class="s-btn s-btn-ghost" type="button" @click="switchToStandard">去普通版创建</button>
      </template>

      <button
        v-for="item in list"
        :key="item.id"
        class="s-card s-card-clickable"
        type="button"
        @click="start(item)"
      >
        <span class="s-h2">{{ item.title }}</span>
        <span v-if="item.description" class="s-muted">{{ item.description }}</span>
        <span class="pick-meta">{{ item.questionCount }} 个问题 · 选它 ›</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import SeniorHeader from '@/components/senior/SeniorHeader.vue';
import { setApi } from '@/api/sets';
import { useUiStore } from '@/stores/ui';

const router = useRouter();
const ui = useUiStore();
const list = ref([]);
const loading = ref(true);
const error = ref('');

async function load() {
  error.value = '';
  try {
    const page = await setApi.mine(0, 50);
    list.value = (page.items || []).filter((s) => !s.takenDown);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function start(item) {
  router.push(`/senior/fill/${item.id}`);
}

function switchToStandard() {
  ui.setMode('standard');
  router.push('/sets/new');
}

onMounted(load);
</script>

<style scoped>
.pick-meta {
  font-size: 18px;
  font-weight: 700;
  color: #12306b;
}
</style>
