<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="title">注册</h1>
      <p class="subtitle">当前版本用账号 + 密码直接注册</p>

      <form @submit.prevent="handleRegister">
        <div class="field">
          <label>账号</label>
          <input v-model.trim="form.account" type="text" placeholder="手机号或邮箱" autocomplete="username" />
        </div>

        <div class="field">
          <label>密码</label>
          <input
            v-model="form.password"
            type="password"
            placeholder="至少 6 位"
            autocomplete="new-password"
          />
        </div>

        <div class="field">
          <label>确认密码</label>
          <input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            autocomplete="new-password"
          />
        </div>

        <p v-if="error" class="error">{{ error }}</p>

        <button class="submit" type="submit" :disabled="loading">
          {{ loading ? '注册中…' : '注 册' }}
        </button>
      </form>

      <div class="links">
        <router-link to="/login">已有账号？去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { userApi } from '@/api/user';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();

const form = reactive({ account: '', password: '', confirmPassword: '' });
const loading = ref(false);
const error = ref('');

const PHONE = /^1[3-9]\d{9}$/;
const EMAIL = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

async function handleRegister() {
  error.value = '';

  if (!form.account || !form.password) {
    error.value = '请填写完整的账号和密码';
    return;
  }
  if (!PHONE.test(form.account) && !EMAIL.test(form.account)) {
    error.value = '账号需为手机号或邮箱';
    return;
  }
  if (form.password.length < 6) {
    error.value = '密码长度不能少于 6 位';
    return;
  }
  if (form.password !== form.confirmPassword) {
    error.value = '两次输入的密码不一致';
    return;
  }

  loading.value = true;
  try {
    const data = await userApi.register(form.account, form.password);
    userStore.setLoginInfo(data);
    router.push('/home');
  } catch (e) {
    error.value = e.message || '注册失败';
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
  text-align: center;
  font-size: 13px;
}

.links a { color: #54a3f8; text-decoration: none; }
.links a:hover { text-decoration: underline; }
</style>
