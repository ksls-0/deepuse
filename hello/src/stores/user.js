import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { userApi } from '@/api/user';

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

  return { token, userId, account, role, isLoggedIn, isAdmin, setLoginInfo, logout, refresh };
});
