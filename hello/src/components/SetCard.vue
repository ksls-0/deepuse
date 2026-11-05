<template>
  <article class="card" :class="{ clickable }" @click="onClick">
    <header class="card-head">
      <h3 class="card-title">{{ set.title }}</h3>
      <span v-if="set.takenDown" class="badge down">已下架</span>
      <span v-else-if="set.visibility === 1" class="badge public">公开</span>
      <span v-else class="badge private">私密</span>
    </header>

    <p class="card-desc">{{ set.description || '暂无简介' }}</p>

    <footer class="card-foot">
      <span class="meta">{{ set.questionCount }} 个问题</span>
      <span class="meta">被导入 {{ set.importCount }}</span>
      <span v-if="set.ownerAccount" class="meta owner">by {{ set.ownerAccount }}</span>
    </footer>
  </article>
</template>

<script setup>
const props = defineProps({
  set: { type: Object, required: true },
  clickable: { type: Boolean, default: true }
});

const emit = defineEmits(['select']);

function onClick() {
  if (props.clickable) {
    emit('select', props.set);
  }
}
</script>

<style scoped>
.card {
  border: 2px solid #e2e2e2;
  border-radius: 20px;
  padding: 18px 20px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.2s ease;
  min-height: 132px;
  box-sizing: border-box;
}

.card.clickable { cursor: pointer; }

.card.clickable:hover {
  border-color: #54a3f8;
  box-shadow: 0 6px 20px rgba(84, 163, 248, 0.16);
  transform: translateY(-2px);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.card-desc {
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
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: #999;
}

.card-foot .owner { margin-left: auto; color: #54a3f8; }
</style>
