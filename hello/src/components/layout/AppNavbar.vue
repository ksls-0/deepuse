<template>
  <nav class="navbar">
    <div class="nav-left">
      <button v-if="showBack" class="nav-back-btn" @click="handleBack">← 返回</button>
      <router-link v-else to="/home" class="brand">角色工坊</router-link>
    </div>

    <div class="nav-center">
      <slot name="center">
        <span class="nav-title">{{ title }}</span>
      </slot>
    </div>

    <div class="nav-right">
      <slot name="right"></slot>

      <!-- 切到长辈版（大字版） -->
      <ModeSwitch />

      <!-- 桌面端：平铺导航 -->
      <nav class="desktop-links">
        <router-link v-for="link in links" :key="link.to" :to="link.to" class="nav-link">
          {{ link.label }}
        </router-link>
      </nav>

      <!-- 移动端：汉堡菜单 -->
      <button class="hamburger" aria-label="打开菜单" @click="menuOpen = !menuOpen">
        {{ menuOpen ? '✕' : '☰' }}
      </button>
    </div>
  </nav>

  <Transition name="drawer">
    <div v-if="menuOpen" class="drawer-mask" @click.self="menuOpen = false">
      <div class="drawer">
        <router-link
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          class="drawer-link"
          @click="menuOpen = false"
        >
          {{ link.label }}
        </router-link>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import ModeSwitch from '@/components/ModeSwitch.vue';

const props = defineProps({
  title: { type: String, default: '' },
  showBack: { type: Boolean, default: true },
  backTo: { type: [String, Object], default: null }
});

const emit = defineEmits(['back']);

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const menuOpen = ref(false);
const isAdmin = computed(() => userStore.isAdmin);

const links = computed(() => {
  // 演示版没有登录页，未登录时也直接给完整导航（进站已自动登录）
  const base = [
    { to: '/home', label: '首页' },
    { to: '/sets', label: '我的模板' },
    { to: '/templates', label: '我的角色' },
    { to: '/conversations', label: '对话' },
    { to: '/explore', label: '广场' }
  ];
  // 管理员多一个审核入口
  if (isAdmin.value) {
    base.push({ to: '/admin', label: '内容审核' });
  }
  return base;
});

// 路由切换后自动收起抽屉，避免遮住新页面
watch(() => route.fullPath, () => {
  menuOpen.value = false;
});

function handleBack() {
  emit('back');
  if (props.backTo) {
    router.push(props.backTo);
  } else {
    router.back();
  }
}
</script>

<style scoped>
.navbar {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 60px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px;
  box-sizing: border-box;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  z-index: 100;
}

.nav-left,
.nav-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nav-left { flex: 0 0 auto; }
.nav-right { flex: 1 1 auto; justify-content: flex-end; }
.nav-center { flex: 1 1 auto; text-align: center; min-width: 0; }

.brand {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #1f1f1f;
  text-decoration: none;
}

.nav-title {
  font-size: 16px;
  font-weight: 600;
  color: #2a2a2a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
  max-width: 100%;
}

.desktop-links {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-link {
  font-size: 14px;
  color: #444;
  text-decoration: none;
  padding: 6px 10px;
  border-radius: 14px;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.nav-link:hover {
  color: #54a3f8;
  background: rgba(84, 163, 248, 0.08);
}

.nav-link.router-link-active {
  color: #54a3f8;
  font-weight: 600;
}

.nav-back-btn {
  background: transparent;
  border: 1px solid #bbb;
  border-radius: 20px;
  padding: 6px 14px;
  font-size: 14px;
  color: #2a2a2a;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.nav-back-btn:hover {
  border-color: #54a3f8;
  color: #54a3f8;
  background-color: rgba(84, 163, 248, 0.1);
}

.logout-btn {
  background: transparent;
  border: 1px solid #ff4d4f;
  border-radius: 20px;
  padding: 6px 14px;
  font-size: 14px;
  color: #ff4d4f;
  cursor: pointer;
  transition: all 0.2s ease;
}

.logout-btn:hover {
  background-color: #ff4d4f;
  color: white;
}

.hamburger {
  display: none;
  width: 36px;
  height: 36px;
  border: 1px solid #dcdcdc;
  background: #fff;
  border-radius: 10px;
  font-size: 16px;
  color: #333;
  cursor: pointer;
}

/* ---------- 移动端抽屉 ---------- */
.drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.32);
  z-index: 200;
  display: flex;
  justify-content: flex-end;
}

.drawer {
  width: 240px;
  background: #fff;
  height: 100%;
  padding: 76px 16px 16px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 6px;
  box-shadow: -8px 0 24px rgba(0, 0, 0, 0.12);
}

.drawer-link {
  display: block;
  padding: 13px 16px;
  border-radius: 12px;
  font-size: 15px;
  color: #333;
  text-decoration: none;
}

.drawer-link:hover {
  background: rgba(84, 163, 248, 0.1);
  color: #54a3f8;
}

.drawer-link.router-link-active {
  background: rgba(84, 163, 248, 0.12);
  color: #54a3f8;
  font-weight: 600;
}

.drawer-logout {
  margin-top: auto;
  padding: 13px 16px;
  border: 1px solid #ff4d4f;
  background: #fff;
  color: #ff4d4f;
  border-radius: 12px;
  font-size: 15px;
  cursor: pointer;
}

.drawer-enter-active,
.drawer-leave-active { transition: opacity 0.2s ease; }
.drawer-enter-from,
.drawer-leave-to { opacity: 0; }

@media (max-width: 900px) {
  .desktop-links { display: none; }
  .hamburger { display: flex; align-items: center; justify-content: center; }
}

@media (max-width: 480px) {
  .nav-center { display: none; }
  .navbar { padding: 0 14px; }
}
</style>
