<template>
  <div class="s-page">
    <SeniorHeader title="我的角色" back-to="/senior" />

    <div class="s-content">
      <div v-if="loading" class="s-text">正在加载…</div>

      <div v-else-if="error" class="s-alert">{{ error }}</div>

      <template v-else-if="list.length === 0">
        <div class="s-card">
          <p class="s-h2">还没有角色</p>
          <p class="s-text">先去「做一个新角色」，答几个问题就能生成一个。</p>
        </div>
        <router-link to="/senior/pick" class="s-btn s-btn-primary">去做一个</router-link>
      </template>

      <template v-else>
        <button
          v-for="item in list"
          :key="item.id"
          class="s-card s-card-clickable char-card"
          type="button"
          :disabled="starting === item.id"
          @click="talkTo(item)"
        >
          <span class="char-top">
            <span class="char-avatar">{{ avatarOf(item) }}</span>
            <span class="char-name">{{ item.name }}</span>
          </span>
          <span v-if="item.tagline" class="char-tagline">{{ item.tagline }}</span>
          <span class="char-action">
            {{ starting === item.id ? '正在准备…' : '和 TA 说话' }}
          </span>
        </button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import SeniorHeader from '@/components/senior/SeniorHeader.vue';
import { templateApi } from '@/api/templates';
import { conversationApi } from '@/api/conversations';

const router = useRouter();
const list = ref([]);
const loading = ref(true);
const error = ref('');
const starting = ref(null);

async function load() {
  error.value = '';
  try {
    const page = await templateApi.mine(0, 50);
    list.value = page.items || [];
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function avatarOf(item) {
  const source = (item.name || '？').trim();
  return source.charAt(0) || '？';
}

/**
 * 点一个角色：能复用已有会话就复用，没有再新建。
 * 老人不关心"会话"这个概念，界面上只说「和 TA 说话」。
 */
async function talkTo(item) {
  if (starting.value) return;
  starting.value = item.id;
  error.value = '';
  try {
    const page = await conversationApi.mine(0, 50);
    const existing = (page.items || []).find((c) => c.templateId === item.id);
    if (existing) {
      router.push(`/senior/chat/${existing.id}`);
      return;
    }
    const created = await conversationApi.create(item.id);
    router.push(`/senior/chat/${created.id}`);
  } catch (e) {
    error.value = e.message;
  } finally {
    starting.value = null;
  }
}

onMounted(load);
</script>

<style scoped>
.char-card {
  gap: 10px;
}

.char-top {
  display: flex;
  align-items: center;
  gap: 14px;
}

.char-avatar {
  flex: 0 0 auto;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 2px solid #12306b;
  color: #12306b;
  font-size: 26px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.char-name {
  font-size: 26px;
  font-weight: 700;
  color: #000;
}

.char-tagline {
  font-size: 19px;
  color: #333;
  line-height: 1.55;
}

.char-action {
  margin-top: 6px;
  min-height: 56px;
  border-radius: 12px;
  background: #12306b;
  color: #fff;
  font-size: 21px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
