import { createRouter, createWebHistory } from 'vue-router';
import { isSeniorMode } from '@/stores/ui';
import { useUserStore } from '@/stores/user';
import { DEMO_MODE } from '@/demo';

/**
 * 演示版（DEMO_MODE）临时下掉了登录/注册。
 * 恢复登录时把下面两段注释打开即可，LoginView / RegisterView / SeniorLoginView 文件都还在：
 *
 *   { path: '/login', name: 'Login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
 *   { path: '/register', name: 'Register', component: () => import('@/views/RegisterView.vue'), meta: { public: true } },
 *   { path: '/senior/login', name: 'SeniorLogin', component: () => import('@/views/senior/SeniorLoginView.vue'), meta: { public: true } },
 *
 * 同时把 src/demo.js 里的 DEMO_MODE 改成 false。
 */
const routes = [
  // 按上次选的界面模式决定首页落在哪边
  { path: '/', redirect: () => (isSeniorMode() ? '/senior' : '/home') },

  { path: '/home', name: 'Home', component: () => import('@/views/HomeView.vue') },

  // 我的模板
  { path: '/sets', name: 'Sets', component: () => import('@/views/SetsListView.vue') },
  { path: '/sets/new', name: 'SetCreate', component: () => import('@/views/SetEditorView.vue') },
  { path: '/sets/:id/edit', name: 'SetEdit', component: () => import('@/views/SetEditorView.vue') },
  { path: '/sets/:id/fill', name: 'SetFill', component: () => import('@/views/SetFillView.vue') },

  // 我的角色
  { path: '/templates', name: 'Templates', component: () => import('@/views/TemplatesListView.vue') },
  { path: '/templates/:id', name: 'TemplateDetail', component: () => import('@/views/TemplateDetailView.vue') },

  // 对话
  { path: '/conversations', name: 'Conversations', component: () => import('@/views/ConversationsListView.vue') },
  { path: '/chat/:id', name: 'Chat', component: () => import('@/views/ChatView.vue') },

  // 管理后台：仅 ADMIN 可见
  { path: '/admin', name: 'Admin', component: () => import('@/views/AdminView.vue'), meta: { admin: true } },

  // 广场：游客可浏览
  { path: '/explore', name: 'Explore', component: () => import('@/views/ExploreView.vue'), meta: { public: true } },
  {
    path: '/explore/sets/:id',
    name: 'PublicSet',
    component: () => import('@/views/PublicSetDetailView.vue'),
    meta: { public: true }
  },
  {
    path: '/explore/templates/:id',
    name: 'PublicTemplate',
    component: () => import('@/views/PublicTemplateDetailView.vue'),
    meta: { public: true }
  },

  // ===================== 长辈版（大字版） =====================
  // 独立的一套页面，只保留「和角色说话 / 做一个新角色 / 帮助」三件事。
  // 和大众版共用同一套 API，数据完全互通。
  {
    path: '/senior',
    name: 'SeniorHome',
    component: () => import('@/views/senior/SeniorHomeView.vue')
  },
  {
    path: '/senior/characters',
    name: 'SeniorCharacters',
    component: () => import('@/views/senior/SeniorCharactersView.vue')
  },
  {
    path: '/senior/pick',
    name: 'SeniorPick',
    component: () => import('@/views/senior/SeniorPickView.vue')
  },
  {
    path: '/senior/fill/:setId',
    name: 'SeniorFill',
    component: () => import('@/views/senior/SeniorFillView.vue')
  },
  {
    path: '/senior/chat/:id',
    name: 'SeniorChat',
    component: () => import('@/views/senior/SeniorChatView.vue')
  },
  {
    path: '/senior/help',
    name: 'SeniorHelp',
    component: () => import('@/views/senior/SeniorHelpView.vue')
  },

  { path: '/:pathMatch(.*)*', redirect: '/' }
];

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
});

router.beforeEach(async (to) => {
  // 演示版：没有 token 就静默登录，不再往登录页跳
  if (DEMO_MODE && !localStorage.getItem('token')) {
    const userStore = useUserStore();
    await userStore.ensureDemoLogin();
  }

  // 管理页仅 ADMIN 可进；服务端也会拦，这里只是体验优化
  if (to.meta.admin && localStorage.getItem('role') !== 'ADMIN') {
    return { path: '/' };
  }
  return true;
});

export default router;
