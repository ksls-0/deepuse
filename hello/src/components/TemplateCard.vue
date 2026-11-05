<template>
  <article class="card" :class="{ clickable }" @click="onClick">
    <div class="avatar">{{ initial }}</div>

    <div class="body">
      <header class="card-head">
        <h3 class="card-title">{{ template.name }}</h3>
        <span v-if="template.takenDown" class="badge down">已下架</span>
        <span v-else-if="template.visibility === 1" class="badge public">公开</span>
        <span v-else class="badge private">私密</span>
      </header>

      <p class="tagline">{{ template.tagline || '暂无一句话人设' }}</p>

      <footer class="card-foot">
        <span class="meta">被使用 {{ template.useCount }}</span>
        <span v-if="template.ownerAccount" class="meta owner">by {{ template.ownerAccount }}</span>
      </footer>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  template: { type: Object, required: true },
  clickable: { type: Boolean, default: true }
});

const emit = defineEmits(['select']);

const initial = computed(() => {
  const name = props.template.name || '?';
  return name.trim().charAt(0).toUpperCase();
});

function onClick() {
  if (props.clickable) {
    emit('select', props.template);
  }
}
</script>

<style scoped>
.card {
  border: 2px solid #e2e2e2;
  border-radius: 20px;
  padding: 16px 18px;
  background: #fff;
  display: flex;
  gap: 14px;
  transition: all 0.2s ease;
  min-height: 120px;
  box-sizing: border-box;
}

.card.clickable { cursor: pointer; }

.card.clickable:hover {
  border-color: #54a3f8;
  box-shadow: 0 6px 20px rgba(84, 163, 248, 0.16);
  transform: translateY(-2px);
}

.avatar {
  width: 46px;
  height: 46px;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, #54a3f8, #a7cdfa);
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f1f1f;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge {
  flex-shrink: 0;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  border: 1px solid currentColor;
}

.badge.public { color: #2e7d32; }
.badge.private { color: #8a8a8a; }
.badge.down { color: #c62828; background: #ffebee; }

.tagline {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-foot {
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: #999;
}

.card-foot .owner { margin-left: auto; color: #54a3f8; }
</style>
