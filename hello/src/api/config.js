import axios from 'axios';

/**
 * baseURL 留空 => 请求走同源，由 vue.config.js 的 devServer.proxy 转发到 8081。
 * 这样开发期完全不依赖 CORS，生产期同源部署也无需改配置。
 */
const api = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || '',
  // 同步生成角色模板可能要 15~60s，超时必须给足
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
      // token 失效：清干净本地状态并回登录页
      if (status === 401) {
        localStorage.removeItem('token');
        localStorage.removeItem('userId');
        localStorage.removeItem('account');
        if (!window.location.pathname.startsWith('/login')) {
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
