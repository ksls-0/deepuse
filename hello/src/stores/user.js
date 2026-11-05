import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { userApi } from '@/api/user';

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '');
  const userId = ref(localStorage.getItem('userId') || '');
  const account = ref(localStorage.getItem('account') || '');

  const isLoggedIn = computed(() => !!token.value);

  function setLoginInfo(data) {
    token.value = data.token || '';
    userId.value = data.userId || '';
    account.value = data.account || '';

    localStorage.setItem('token', token.value);
    localStorage.setItem('userId', userId.value);
    localStorage.setItem('account', account.value);
  }

  function logout() {
    token.value = '';
    userId.value = '';
    account.value = '';

    localStorage.removeItem('token');
    localStorage.removeItem('userId');
    localStorage.removeItem('account');
  }

  /** 用 /user/me 校验 token 是否还有效，顺带同步账号信息 */
  async function refresh() {
    if (!token.value) return null;
    try {
      const me = await userApi.me();
      account.value = me.account;
      localStorage.setItem('account', me.account);
      return me;
    } catch (e) {
      logout();
      return null;
    }
  }

  return { token, userId, account, isLoggedIn, setLoginInfo, logout, refresh };
});
