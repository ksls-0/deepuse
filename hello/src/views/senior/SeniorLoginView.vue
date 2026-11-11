<template>
  <div class="login-wrap">
    <div class="login-box">
      <h1 class="s-h1">登录</h1>
      <p class="s-text">用你的账号登录，然后就能和角色说话。</p>

      <form class="form" @submit.prevent="submit">
        <div>
          <label class="s-label" for="senior-account">账号（邮箱或手机号）</label>
          <input
            id="senior-account"
            v-model.trim="account"
            class="s-input"
            type="text"
            inputmode="email"
            autocomplete="username"
            placeholder="例如 name@example.com"
          />
        </div>

        <div>
          <label class="s-label" for="senior-password">密码</label>
          <input
            id="senior-password"
            v-model="password"
            class="s-input"
            type="password"
            autocomplete="current-password"
            placeholder="输入密码"
          />
        </div>

        <p v-if="error" class="s-alert">{{ error }}</p>

        <button class="s-btn s-btn-primary" type="submit" :disabled="loading">
          {{ loading ? '正在登录…' : '登 录' }}
        </button>
      </form>

      <div class="tip-box">
        <p class="s-muted">还没有账号？</p>
        <p class="s-muted">注册需要收一条验证码，建议请家里人帮你注册好，再回来登录。</p>
      </div>

      <button class="s-btn s-btn-ghost" type="button" @click="switchToStandard">
        换成普通版
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { userApi } from '@/api/user';
import { useUserStore } from '@/stores/user';
import { useUiStore } from '@/stores/ui';

const router = useRouter();
const userStore = useUserStore();
const ui = useUiStore();

const account = ref('');
const password = ref('');
const loading = ref(false);
const error = ref('');

const EMAIL = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const PHONE = /^1[3-9]\d{9}$/;

async function submit() {
  error.value = '';
  if (!account.value) {
    error.value = '请填写账号';
    return;
  }

  const isEmail = EMAIL.test(account.value);
  const isPhone = PHONE.test(account.value);
  if (!isEmail && !isPhone) {
    error.value = '账号要填邮箱（如 name@example.com）或 11 位手机号';
    return;
  }
  if (!password.value) {
    error.value = '请填写密码';
    return;
  }

  loading.value = true;
  try {
    const data = isEmail
      ? await userApi.loginEmail(account.value, password.value)
      : await userApi.loginPhone(account.value, password.value);
    userStore.setLoginInfo(data);
    router.push('/senior');
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function switchToStandard() {
  ui.setMode('standard');
  router.push('/login');
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  box-sizing: border-box;
}

.login-box {
  width: 100%;
  max-width: 560px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.tip-box {
  border: 2px dashed #777;
  border-radius: 14px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
