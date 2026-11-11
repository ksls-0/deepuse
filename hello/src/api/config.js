import axios from 'axios';
import { DEMO_MODE } from '@/demo';

/**
 * baseURL 留空 => 请求走同源，由 vue.config.js 的 devServer.proxy 转发到 8081。
 * 这样开发期完全不依赖 CORS，生产期同源部署也无需改配置。
 */
const api = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || '',
  // 同步生成角色可能要 15~60s，超时必须给足
  timeout: 120000,
  headers: { 'Content-Type': 'application/json' }
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res && res.code === 0) {
      return res.data;
    }
    return Promise.reject(new Error((res && res.message) || '请求失败'));
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response;
      // token 失效
      if (status === 401) {
        localStorage.removeItem('token');
        localStorage.removeItem('userId');
        localStorage.removeItem('account');
        localStorage.removeItem('role');
        if (DEMO_MODE) {
          // 演示版没有登录页：清掉 token 后回首页，路由守卫会自动重新静默登录。
          // 用 sessionStorage 限流，避免登录本身也 401 时无限刷新。
          const last = Number(sessionStorage.getItem('demoReloginAt') || 0);
          if (Date.now() - last > 10000) {
            sessionStorage.setItem('demoReloginAt', String(Date.now()));
            window.location.replace('/');
          }
        } else if (!window.location.pathname.startsWith('/login')) {
          window.location.href = '/login';
        }
      }
      return Promise.reject(new Error((data && data.message) || `请求失败（HTTP ${status}）`));
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时，AI 生成较慢时请重试'));
    }
    return Promise.reject(new Error('网络异常，请检查后端服务是否启动'));
  }
);

export default api;
