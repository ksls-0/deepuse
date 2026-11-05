import { createRouter, createWebHistory } from 'vue-router';

const routes = [
  { path: '/', redirect: '/home' },

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

  // 广场：游客可浏览
  // 管理后台：仅 ADMIN 可见
  { path: '/admin', name: 'Admin', component: () => import('@/views/AdminView.vue'), meta: { admin: true } },

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

  { path: '/:pathMatch(.*)*', redirect: '/home' }
];

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
});

router.beforeEach((to) => {
  const token = localStorage.getItem('token');
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if ((to.path === '/login' || to.path === '/register') && token) {
    return { path: '/home' };
  }
  // 管理页仅 ADMIN 可进；服务端也会拦，这里只是体验优化
  if (to.meta.admin && localStorage.getItem('role') !== 'ADMIN') {
    return { path: '/home' };
  }
  return true;
});

export default router;
