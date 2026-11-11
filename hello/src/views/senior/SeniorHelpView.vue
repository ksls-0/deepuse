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
      </div>

      <div class="s-card">
        <p class="s-h2">退出登录</p>
        <p class="s-text">退出后需要重新输入账号和密码。</p>

        <p v-if="logoutHint" class="s-alert">{{ logoutHint }}</p>

        <button
          :class="['s-btn', confirming ? 's-btn-danger' : 's-btn-ghost']"
          type="button"
          :disabled="loggingOut"
          @click="handleLogout"
        >
          {{ loggingOut ? '正在退出…' : confirming ? '再点一次，确认退出' : '退出登录' }}
        </button>

        <button v-if="confirming" class="s-btn s-btn-ghost" type="button" @click="cancelLogout">
          我不退了
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import SeniorHeader from '@/components/senior/SeniorHeader.vue';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();

const account = ref('');
const confirming = ref(false);
const loggingOut = ref(false);
const logoutHint = ref('');
let confirmTimer = null;

/**
 * 老人容易误触，退出必须两步：
 * 第一次点只是变成「再点一次，确认退出」，5 秒内不点就自动取消。
 */
function handleLogout() {
  if (!confirming.value) {
    confirming.value = true;
    logoutHint.value = '为防止点错，需要再点一次才会退出。';
    clearTimeout(confirmTimer);
    confirmTimer = setTimeout(cancelLogout, 5000);
    return;
  }

  clearTimeout(confirmTimer);
  loggingOut.value = true;
  userStore.logout();
  router.push('/senior/login');
}

function cancelLogout() {
  confirming.value = false;
  logoutHint.value = '';
  clearTimeout(confirmTimer);
  confirmTimer = null;
}

onMounted(() => {
  account.value = userStore.account || localStorage.getItem('account') || '';
});

onBeforeUnmount(() => clearTimeout(confirmTimer));
</script>

<style scoped>
.s-btn {
  margin-top: 4px;
}
</style>
