import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { userApi } from '@/api/user';
import { DEMO_ACCOUNT, DEMO_PASSWORD } from '@/demo';

const PHONE = /^1[3-9]\d{9}$/;

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '');
  const userId = ref(localStorage.getItem('userId') || '');
  const account = ref(localStorage.getItem('account') || '');
  const role = ref(localStorage.getItem('role') || 'USER');

  const isLoggedIn = computed(() => !!token.value);
  const isAdmin = computed(() => role.value === 'ADMIN');

  function setLoginInfo(data) {
    token.value = data.token || '';
    userId.value = data.userId || '';
    account.value = data.account || '';
    role.value = data.role || 'USER';

    localStorage.setItem('token', token.value);
    localStorage.setItem('userId', userId.value);
    localStorage.setItem('account', account.value);
    localStorage.setItem('role', role.value);
  }

  function logout() {
    token.value = '';
    userId.value = '';
    account.value = '';
    role.value = 'USER';

    localStorage.removeItem('token');
    localStorage.removeItem('userId');
    localStorage.removeItem('account');
    localStorage.removeItem('role');
  }

  /** 用 /user/me 校验 token 是否还有效，顺带同步账号与角色 */
  async function refresh() {
    if (!token.value) return null;
    try {
      const me = await userApi.me();
      account.value = me.account;
      role.value = me.role || 'USER';
      localStorage.setItem('account', me.account);
      localStorage.setItem('role', role.value);
      return me;
    } catch (e) {
      logout();
      return null;
    }
  }

  /**
   * 演示模式静默登录：演示版没有登录页，进站直接拿固定账号换 token。
   *
   * 并发安全：多个路由守卫同时触发时只发一次请求（共享同一个 promise）。
   * 失败返回 false，调用方照常放行，让接口层把真实错误暴露出来，
   * 而不是卡在一个看不见的登录页上。
   */
  let pending = null;
  async function ensureDemoLogin() {
    if (token.value) return true;
    if (pending) return pending;

    pending = (async () => {
      try {
        const data = PHONE.test(DEMO_ACCOUNT)
          ? await userApi.loginPhone(DEMO_ACCOUNT, DEMO_PASSWORD)
          : await userApi.loginEmail(DEMO_ACCOUNT, DEMO_PASSWORD);
        setLoginInfo(data);
        return true;
      } catch (e) {
        console.warn('[demo] 静默登录失败：', e.message);
        return false;
      } finally {
        pending = null;
      }
    })();

    return pending;
  }

  return {
    token,
    userId,
    account,
    role,
    isLoggedIn,
    isAdmin,
    setLoginInfo,
    logout,
    refresh,
    ensureDemoLogin
  };
});
