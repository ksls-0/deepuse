import { defineStore } from 'pinia';
import { computed, ref, watch } from 'vue';

/**
 * 界面模式。同一个应用两套皮：
 *
 *   standard —— 大众版（现在的界面，功能齐全）
 *   senior   —— 长辈版（大字号、大按钮、高对比、只留 3 件事）
 *
 * 选择存 localStorage，刷新和下次打开都记得。
 * 切换时给 <html> 加 class="mode-senior"，全局样式据此关掉动画与毛玻璃。
 */
const STORAGE_KEY = 'ui_mode';

export const MODE_STANDARD = 'standard';
export const MODE_SENIOR = 'senior';

export const useUiStore = defineStore('ui', () => {
  const mode = ref(
    localStorage.getItem(STORAGE_KEY) === MODE_SENIOR ? MODE_SENIOR : MODE_STANDARD
  );

  const isSenior = computed(() => mode.value === MODE_SENIOR);

  function applyToDom() {
    // 直接写 html 上的 class，样式在 styles/senior.css 里统一接管
    document.documentElement.classList.toggle('mode-senior', mode.value === MODE_SENIOR);
  }

  function setMode(next) {
    mode.value = next === MODE_SENIOR ? MODE_SENIOR : MODE_STANDARD;
  }

  function toggle() {
    setMode(mode.value === MODE_SENIOR ? MODE_STANDARD : MODE_SENIOR);
  }

  watch(
    mode,
    (value) => {
      localStorage.setItem(STORAGE_KEY, value);
      applyToDom();
    },
    { immediate: true }
  );

  return { mode, isSenior, setMode, toggle };
});

/** 给路由守卫等非组件场景用（拿不到 pinia 实例时读 localStorage 即可） */
export function isSeniorMode() {
  return localStorage.getItem(STORAGE_KEY) === MODE_SENIOR;
}
