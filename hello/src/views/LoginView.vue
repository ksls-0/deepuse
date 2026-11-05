<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="title">登录</h1>
      <p class="subtitle">登录后可创建题集、生成并分享角色</p>

      <form @submit.prevent="handleLogin">
        <div class="field">
          <label>账号</label>
          <input v-model.trim="form.account" type="text" placeholder="手机号或邮箱" autocomplete="username" />
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
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { userApi } from '@/api/user';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const form = reactive({ account: '', password: '' });
const loading = ref(false);
const error = ref('');

async function handleLogin() {
  error.value = '';
  if (!form.account || !form.password) {
    error.value = '请填写账号和密码';
    return;
  }

  loading.value = true;
  try {
    const data = await userApi.login(form.account, form.password);
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
  max-width: 400px;
  padding: 40px;
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
  margin-bottom: 28px;
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
}

.links a { color: #54a3f8; text-decoration: none; }
.links a:hover { text-decoration: underline; }
</style>
