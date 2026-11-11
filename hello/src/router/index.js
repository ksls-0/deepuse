import { createRouter, createWebHistory } from 'vue-router';
import { isSeniorMode } from '@/stores/ui';

const routes = [
  // 按上次选的界面模式决定首页落在哪边
  { path: '/', redirect: () => (isSeniorMode() ? '/senior' : '/home') },

  { path: '/login', name: 'Login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/register', name: 'Register', component: () => import('@/views/RegisterView.vue'), meta: { public: true } },

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
    path: '/senior/login',
    name: 'SeniorLogin',
    component: () => import('@/views/senior/SeniorLoginView.vue'),
    meta: { public: true }
  },
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

  { path: '/:pathMatch(.*)*', redirect: '/home' }
];

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
});

router.beforeEach((to) => {
  const token = localStorage.getItem('token');
  const senior = isSeniorMode();

  // 未登录：跳对应的登录页（长辈版留在长辈版里，别把老人扔到小字界面）
  if (!to.meta.public && !token) {
    return { path: senior ? '/senior/login' : '/login', query: { redirect: to.fullPath } };
  }

  // 已登录还去登录页：送回各自的首页
  const isLoginPage = to.path === '/login' || to.path === '/register' || to.path === '/senior/login';
  if (isLoginPage && token) {
    return { path: senior ? '/senior' : '/home' };
  }

  // 管理页仅 ADMIN 可进；服务端也会拦，这里只是体验优化
  if (to.meta.admin && localStorage.getItem('role') !== 'ADMIN') {
    return { path: '/home' };
  }
  return true;
});

export default router;
