<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="title">注册</h1>
      <p class="subtitle">选择一种方式创建账号</p>

      <div class="tabs">
        <button
          :class="['tab', { active: mode === 'email' }]"
          type="button"
          @click="switchMode('email')"
        >邮箱注册</button>
        <button
          :class="['tab', { active: mode === 'phone' }]"
          type="button"
          @click="switchMode('phone')"
        >手机号注册</button>
      </div>

      <form @submit.prevent="handleRegister">
        <div class="field">
          <label>{{ isEmail ? '邮箱' : '手机号' }}</label>
          <input
            v-model.trim="form.target"
            :type="isEmail ? 'email' : 'tel'"
            :placeholder="isEmail ? 'you@example.com' : '11 位手机号'"
            :autocomplete="isEmail ? 'email' : 'tel'"
          />
        </div>

        <div class="field">
          <label>验证码</label>
          <div class="code-row">
            <input
              v-model.trim="form.code"
              type="text"
              inputmode="numeric"
              maxlength="6"
              placeholder="6 位数字"
              class="code-input"
              autocomplete="one-time-code"
            />
            <button
              class="send-btn"
              type="button"
              :disabled="countdown > 0 || sendingCode || !form.target"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}s` : sendingCode ? '发送中…' : '发送验证码' }}
            </button>
          </div>
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
        <p v-if="notice" class="notice">{{ notice }}</p>

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
import { computed, onUnmounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { userApi } from '@/api/user';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();

const PHONE = /^1[3-9]\d{9}$/;
const EMAIL = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

const mode = ref('email');
const isEmail = computed(() => mode.value === 'email');

const form = reactive({ target: '', code: '', password: '', confirmPassword: '' });
const loading = ref(false);
const sendingCode = ref(false);
const countdown = ref(0);
const error = ref('');
const notice = ref('');

let timer = null;

function stopTimer() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

function startCountdown() {
  stopTimer();
  countdown.value = 60;
  timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) stopTimer();
  }, 1000);
}

function switchMode(next) {
  if (mode.value === next) return;
  mode.value = next;
  // 换通道后旧验证码不再有效，清掉避免误用
  form.target = '';
  form.code = '';
  error.value = '';
  notice.value = '';
  stopTimer();
  countdown.value = 0;
}

async function handleSendCode() {
  error.value = '';
  notice.value = '';

  if (!form.target) {
    error.value = isEmail.value ? '请先填写邮箱' : '请先填写手机号';
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

  sendingCode.value = true;
  try {
    if (isEmail.value) {
      await userApi.sendEmailCode(form.target);
    } else {
      await userApi.sendPhoneCode(form.target);
    }
    notice.value =
      '验证码已发送。当前为开发模式，未真实发信，验证码打印在后端日志里。';
    startCountdown();
  } catch (e) {
    error.value = e.message || '发送验证码失败';
  } finally {
    sendingCode.value = false;
  }
}

async function handleRegister() {
  error.value = '';
  notice.value = '';

  if (!form.target || !form.code || !form.password || !form.confirmPassword) {
    error.value = '请填写完整信息';
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
  if (!/^\d{6}$/.test(form.code)) {
    error.value = '验证码为 6 位数字';
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
    const data = isEmail.value
      ? await userApi.registerEmail(form.target, form.code, form.password)
      : await userApi.registerPhone(form.target, form.code, form.password);
    userStore.setLoginInfo(data);
    router.push('/home');
  } catch (e) {
    error.value = e.message || '注册失败';
  } finally {
    loading.value = false;
  }
}

onUnmounted(stopTimer);
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

.field { margin-bottom: 16px; }

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

.code-row { display: flex; gap: 10px; }

.code-input { flex: 1; }

.send-btn {
  flex-shrink: 0;
  padding: 0 16px;
  background: #54a3f8;
  color: #fff;
  border: none;
  border-radius: 12px;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.2s;
}

.send-btn:hover:not(:disabled) { background: #3d8be6; }
.send-btn:disabled { opacity: 0.55; cursor: not-allowed; }

.error {
  font-size: 13px;
  color: #c62828;
  background: #ffebee;
  border-radius: 10px;
  padding: 8px 12px;
  margin-bottom: 14px;
}

.notice {
  font-size: 13px;
  color: #2e7d32;
  background: #e8f5e9;
  border-radius: 10px;
  padding: 8px 12px;
  margin-bottom: 14px;
  line-height: 1.5;
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
  margin-top: 4px;
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
