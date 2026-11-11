<template>
  <button :class="['mode-switch', isSenior ? 'to-standard' : 'to-senior']" type="button" @click="switchMode">
    {{ isSenior ? '普通版' : '长辈模式' }}
  </button>
</template>

<script setup>
/**
 * 大众版 / 长辈版切换按钮。
 * 两边都在用：大众版放在导航栏，长辈版放在页头。
 */
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useUiStore } from '@/stores/ui';

const router = useRouter();
const ui = useUiStore();
const isSenior = computed(() => ui.isSenior);

function switchMode() {
  ui.toggle();
  // 切过去就落在对应的首页，避免长辈版停在大众版的复杂页面上
  router.push(ui.isSenior ? '/senior' : '/home');
}
</script>

<style scoped>
.mode-switch {
  border-radius: 16px;
  padding: 6px 14px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
}

/* 大众版里的样子：低调，别抢视线 */
.to-senior {
  border: 1px solid #d0d0d0;
  background: #fff;
  color: #333;
}

.to-senior:hover {
  border-color: #54a3f8;
  color: #54a3f8;
  background: rgba(84, 163, 248, 0.08);
}

/* 长辈版里的样子：加大加粗，老人也要看得见、点得准 */
.to-standard {
  border: 2px solid #444;
  background: #fff;
  color: #111;
  font-size: 18px;
  min-height: 48px;
  padding: 8px 16px;
}
</style>
