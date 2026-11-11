<template>
  <header class="s-header">
    <button v-if="backTo" class="s-header-btn" type="button" @click="goBack">← 返回</button>
    <span class="s-header-title">{{ title }}</span>
    <button v-if="showSwitch" class="s-switch-btn" type="button" @click="switchToStandard">
      普通版
    </button>
  </header>
</template>

<script setup>
/**
 * 长辈版统一页头：大返回按钮 + 标题 + 「普通版」切换。
 */
import { useRouter } from 'vue-router';
import { useUiStore } from '@/stores/ui';

const props = defineProps({
  title: { type: String, default: '' },
  backTo: { type: [String, Object], default: null },
  showSwitch: { type: Boolean, default: true }
});

const router = useRouter();
const ui = useUiStore();

function goBack() {
  if (props.backTo) {
    router.push(props.backTo);
  } else {
    router.push('/senior');
  }
}

function switchToStandard() {
  ui.setMode('standard');
  router.push('/home');
}
</script>
