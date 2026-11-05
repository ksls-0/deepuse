<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="title">登录</h1>
      <p class="subtitle">登录后可创建模板、生成并分享角色</p>

      <div class="tabs">
        <button
          :class="['tab', { active: mode === 'email' }]"
          type="button"
          @click="switchMode('email')"
        >邮箱登录</button>
        <button
          :class="['tab', { active: mode === 'phone' }]"
          type="button"
          @click="switchMode('phone')"
        >手机号登录</button>
      </div>

      <form @submit.prevent="handleLogin">
        <div class="field">
          <label>{{ isEmail ? '邮箱' : '手机号' }}</label>
          <input
            v-model.trim="form.target"
            :type="isEmail ? 'email' : 'tel'"
            :placeholder="isEmail ? 'you@example.com' : '11 位手机号'"
            :autocomplete="isEmail ? 'username' : 'username'"
          />
        </div>

        <div class="field">
          <label>密码</label>
          <input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>

        <p v-if="error" class="error">{{ error }}</p>

        <button class="submit" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登 录' }}
        </button>
      </form>

      <div class="links">
        <router-link to="/register">还没有账号？去注册</router-link>
        <router-link to="/explore">先去广场逛逛 →</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { userApi } from '@/api/user';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const PHONE = /^1[3-9]\d{9}$/;
const EMAIL = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

const mode = ref('email');
const isEmail = computed(() => mode.value === 'email');

const form = reactive({ target: '', password: '' });
const loading = ref(false);
const error = ref('');

function switchMode(next) {
  if (mode.value === next) return;
  mode.value = next;
  form.target = '';
  error.value = '';
}

async function handleLogin() {
  error.value = '';

  if (!form.target || !form.password) {
    error.value = isEmail.value ? '请填写邮箱和密码' : '请填写手机号和密码';
    return;
  }
  if (isEmail.value && !EMAIL.test(form.target)) {
    error.value = '邮箱格式不正确';
    return;
  }
  if (!isEmail.value && !PHONE.test(form.target)) {
    error.value = '手机号格式不正确';
    return;
  }

  loading.value = true;
  try {
    const data = isEmail.value
      ? await userApi.loginEmail(form.target, form.password)
      : await userApi.loginPhone(form.target, form.password);
    userStore.setLoginInfo(data);
    router.push(route.query.redirect || '/home');
  } catch (e) {
    error.value = e.message || '登录失败';
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #dfe7f1 0%, #ffffff 100%);
  padding: 20px;
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 36px 40px 40px;
  background: #fff;
  border-radius: 24px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
}

.title {
  font-size: 24px;
  color: #1f1f1f;
  text-align: center;
  margin-bottom: 8px;
}

.subtitle {
  font-size: 13px;
  color: #8a8a8a;
  text-align: center;
  margin-bottom: 22px;
}

.tabs {
  display: flex;
  gap: 6px;
  background: #eef2f7;
  border-radius: 16px;
  padding: 4px;
  margin-bottom: 24px;
}

.tab {
  flex: 1;
  border: none;
  background: transparent;
  padding: 10px 0;
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

.field { margin-bottom: 18px; }

.field label {
  display: block;
  font-size: 13px;
  color: #555;
  margin-bottom: 8px;
}

.field input {
  width: 100%;
  padding: 12px 14px;
  border: 2px solid #e2e2e2;
  border-radius: 12px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.2s;
}

.field input:focus { border-color: #54a3f8; }

.error {
  font-size: 13px;
  color: #c62828;
  background: #ffebee;
  border-radius: 10px;
  padding: 8px 12px;
  margin-bottom: 16px;
}

.submit {
  width: 100%;
  padding: 13px;
  background: #54a3f8;
  color: #fff;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  letter-spacing: 2px;
  cursor: pointer;
  transition: background 0.2s;
}

.submit:hover:not(:disabled) { background: #3d8be6; }
.submit:disabled { opacity: 0.6; cursor: not-allowed; }

.links {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  flex-wrap: wrap;
  gap: 8px;
}

.links a { color: #54a3f8; text-decoration: none; }
.links a:hover { text-decoration: underline; }
</style>
