<template>
  <div class="s-page">
    <SeniorHeader title="帮助" back-to="/senior" />

    <div class="s-content">
      <div class="s-card">
        <p class="s-h2">这个页面能做什么</p>
        <p class="s-text">1. 和角色说话：点开一个角色，就能像发微信一样跟它聊天。</p>
        <p class="s-text">2. 做一个新角色：选一个模板，照着问题慢慢写，AI 会帮你写成一个能聊天的角色。</p>
        <p class="s-text">3. 字太小：点右上角「普通版」可以换回原来的界面。</p>
      </div>

      <div class="s-card">
        <p class="s-h2">当前账号</p>
        <p class="s-text">{{ account || '（未获取到账号）' }}</p>
        <p class="s-muted">演示版已自动登录，不需要再输账号和密码。</p>
      </div>

      <router-link to="/senior" class="s-btn s-btn-primary">回到首页</router-link>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import SeniorHeader from '@/components/senior/SeniorHeader.vue';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const account = ref('');

onMounted(() => {
  account.value = userStore.account || localStorage.getItem('account') || '';
});
</script>
