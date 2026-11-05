<template>
  <div class="page">
    <AppNavbar title="" :show-back="false" />

    <main class="content">
      <section class="hero">
        <h1>角色工坊</h1>
        <p>写下你的问题 → 填出自己的答案 → 让 AI 生成一个可对话的角色 → 分享给所有人</p>
      </section>

      <section class="flow">
        <div class="step">
          <span class="num">1</span>
          <h3>创建题集</h3>
          <p>自己定义一组问题，随时编辑增删</p>
        </div>
        <div class="arrow">→</div>
        <div class="step">
          <span class="num">2</span>
          <h3>填写答卷</h3>
          <p>同一题集可保存多份不同答案</p>
        </div>
        <div class="arrow">→</div>
        <div class="step">
          <span class="num">3</span>
          <h3>生成模板</h3>
          <p>DeepSeek 依问答产出结构化角色卡</p>
        </div>
        <div class="arrow">→</div>
        <div class="step">
          <span class="num">4</span>
          <h3>开始对话</h3>
          <p>角色带上下文记忆，可公开给他人使用</p>
        </div>
      </section>

      <section class="actions">
        <router-link to="/sets/new" class="action-card primary">
          <h3>＋ 新建题集</h3>
          <p>从零开始写一组问题</p>
        </router-link>
        <router-link to="/sets" class="action-card">
          <h3>我的题集</h3>
          <p>{{ counts.sets }} 个题集</p>
        </router-link>
        <router-link to="/templates" class="action-card">
          <h3>我的模板</h3>
          <p>{{ counts.templates }} 个角色模板</p>
        </router-link>
        <router-link to="/explore" class="action-card">
          <h3>去广场看看</h3>
          <p>浏览他人公开的题集与模板</p>
        </router-link>
      </section>

      <section v-if="recent.length" class="recent">
        <div class="recent-head">
          <h2>最近的对话</h2>
          <router-link to="/conversations">全部 →</router-link>
        </div>
        <div class="recent-list">
          <router-link
            v-for="c in recent"
            :key="c.id"
            :to="`/chat/${c.id}`"
            class="recent-item"
          >
            <span class="recent-title">{{ c.title || c.templateName }}</span>
            <span class="recent-time">{{ formatTime(c.updatedAt) }}</span>
          </router-link>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import AppNavbar from '@/components/layout/AppNavbar.vue';
import { setApi } from '@/api/sets';
import { templateApi } from '@/api/templates';
import { conversationApi } from '@/api/conversations';

const counts = reactive({ sets: 0, templates: 0 });
const recent = ref([]);

onMounted(async () => {
  try {
    const [sets, templates, conversations] = await Promise.all([
      setApi.mine(0, 1),
      templateApi.mine(0, 1),
      conversationApi.mine(0, 3)
    ]);
    counts.sets = sets.total;
    counts.templates = templates.total;
    recent.value = conversations.items || [];
  } catch (e) {
    // 首页只是概览，失败不打扰用户
    console.warn('首页数据加载失败:', e.message);
  }
});

function formatTime(value) {
  if (!value) return '';
  return new Date(value).toLocaleString();
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f3f6fa 0%, #ffffff 60%);
  padding-top: 60px;
  box-sizing: border-box;
}

.content {
  max-width: 960px;
  margin: 0 auto;
  padding: 40px 20px 60px;
}

.hero { text-align: center; margin-bottom: 44px; }

.hero h1 {
  font-size: 32px;
  color: #1f1f1f;
  letter-spacing: 4px;
  margin-bottom: 12px;
}

.hero p {
  font-size: 15px;
  color: #757575;
}

.flow {
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 44px;
}

.step {
  flex: 1 1 150px;
  max-width: 180px;
  background: #fff;
  border: 2px solid #ececec;
  border-radius: 18px;
  padding: 16px;
  text-align: center;
}

.step .num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #54a3f8;
  color: #fff;
  font-size: 13px;
  margin-bottom: 8px;
}

.step h3 {
  font-size: 14px;
  color: #1f1f1f;
  margin-bottom: 6px;
}

.step p {
  font-size: 12px;
  color: #8a8a8a;
  line-height: 1.5;
}

.arrow {
  display: flex;
  align-items: center;
  color: #c9c9c9;
  font-size: 18px;
}

.actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 40px;
}

.action-card {
  display: block;
  text-decoration: none;
  border: 2px solid #ececec;
  border-radius: 20px;
  padding: 20px;
  background: #fff;
  transition: all 0.2s ease;
}

.action-card:hover {
  border-color: #54a3f8;
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(84, 163, 248, 0.15);
}

.action-card.primary {
  border-color: #54a3f8;
  background: linear-gradient(135deg, #54a3f8, #7bb8fb);
}

.action-card.primary h3,
.action-card.primary p { color: #fff; }

.action-card h3 {
  font-size: 16px;
  color: #1f1f1f;
  margin-bottom: 6px;
}

.action-card p {
  font-size: 13px;
  color: #8a8a8a;
}

.recent-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.recent-head h2 { font-size: 17px; color: #1f1f1f; }
.recent-head a { font-size: 13px; color: #54a3f8; text-decoration: none; }

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  text-decoration: none;
  border: 1px solid #ececec;
  border-radius: 14px;
  padding: 12px 16px;
  background: #fff;
  transition: all 0.2s;
}

.recent-item:hover { border-color: #54a3f8; }

.recent-title { font-size: 14px; color: #2a2a2a; }
.recent-time { font-size: 12px; color: #a5a5a5; }
</style>
