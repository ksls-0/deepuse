<template>
  <section class="character-card">
    <header class="cc-head">
      <div class="avatar">{{ initial }}</div>
      <div class="cc-title">
        <h2>{{ card.name || template.name || '未命名角色' }}</h2>
        <p class="tagline">{{ card.tagline || '暂无一句话人设' }}</p>
      </div>
      <span v-if="isMock" class="mock-flag" title="后端未配置 DEEPSEEK_API_KEY，当前为占位数据">
        示例数据
      </span>
    </header>

    <div v-if="personality.length" class="chips">
      <span v-for="(tag, i) in personality" :key="i" class="chip">{{ tag }}</span>
    </div>

    <dl class="fields">
      <template v-if="card.background">
        <dt>背景故事</dt>
        <dd class="multiline">{{ card.background }}</dd>
      </template>

      <template v-if="card.speakingStyle">
        <dt>语言风格</dt>
        <dd>{{ card.speakingStyle }}</dd>
      </template>

      <template v-if="catchphrases.length">
        <dt>口头禅</dt>
        <dd>
          <span v-for="(p, i) in catchphrases" :key="i" class="quote">「{{ p }}」</span>
        </dd>
      </template>

      <template v-if="taboos.length">
        <dt>不会做的事</dt>
        <dd>
          <span v-for="(t, i) in taboos" :key="i" class="taboo">{{ t }}</span>
        </dd>
      </template>
    </dl>

    <div v-if="dialogues.length" class="dialogues">
      <h3>示例对话</h3>
      <div v-for="(d, i) in dialogues" :key="i" class="dialogue-pair">
        <div class="bubble user"><span class="who">用户</span>{{ d.user }}</div>
        <div class="bubble assistant"><span class="who">{{ card.name || '角色' }}</span>{{ d.assistant }}</div>
      </div>
    </div>

    <details v-if="template.content" class="prompt-block">
      <summary>查看 system prompt（对话时实际注入的文本）</summary>
      <pre>{{ template.content }}</pre>
      <button class="copy-btn" @click="copyPrompt">{{ copied ? '已复制 ✓' : '复制提示词' }}</button>
    </details>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
  template: { type: Object, required: true }
});

const copied = ref(false);

const card = computed(() => props.template.structured || {});

const initial = computed(() => {
  const name = card.value.name || props.template.name || '?';
  return String(name).trim().charAt(0).toUpperCase();
});

const isMock = computed(() => props.template.genModel === 'mock');

const personality = computed(() => toArray(card.value.personality));
const catchphrases = computed(() => toArray(card.value.catchphrases));
const taboos = computed(() => toArray(card.value.taboos));
const dialogues = computed(() => {
  const list = card.value.exampleDialogue;
  return Array.isArray(list) ? list : [];
});

function toArray(value) {
  if (Array.isArray(value)) return value;
  if (typeof value === 'string' && value.trim()) return [value];
  return [];
}

async function copyPrompt() {
  try {
    await navigator.clipboard.writeText(props.template.content || '');
    copied.value = true;
    setTimeout(() => (copied.value = false), 1800);
  } catch (e) {
    // 剪贴板不可用时退化为选中提示
    window.prompt('复制下面的提示词：', props.template.content || '');
  }
}
</script>

<style scoped>
.character-card {
  border: 2px solid #e2e2e2;
  border-radius: 24px;
  background: #fff;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  box-sizing: border-box;
}

.cc-head {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  flex-shrink: 0;
  background: linear-gradient(135deg, #54a3f8, #a7cdfa);
  color: #fff;
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cc-title { flex: 1; min-width: 0; }

.cc-title h2 {
  font-size: 20px;
  color: #1f1f1f;
  margin-bottom: 4px;
}

.tagline {
  font-size: 13px;
  color: #777;
}

.mock-flag {
  flex-shrink: 0;
  font-size: 12px;
  color: #b26a00;
  border: 1px solid #f0c36d;
  background: #fff8e6;
  padding: 3px 10px;
  border-radius: 10px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  font-size: 13px;
  color: #2f6fb5;
  background: rgba(84, 163, 248, 0.12);
  border-radius: 12px;
  padding: 4px 12px;
}

.fields {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 10px 14px;
  align-items: start;
}

.fields dt {
  font-size: 13px;
  color: #8a8a8a;
  padding-top: 2px;
}

.fields dd {
  font-size: 14px;
  color: #2a2a2a;
  line-height: 1.7;
}

.multiline { white-space: pre-wrap; }

.quote {
  display: inline-block;
  margin-right: 8px;
  color: #2f6fb5;
}

.taboo {
  display: inline-block;
  font-size: 13px;
  color: #b23b3b;
  background: #fdecec;
  border-radius: 10px;
  padding: 2px 10px;
  margin: 0 8px 6px 0;
}

.dialogues h3 {
  font-size: 15px;
  color: #1f1f1f;
  margin-bottom: 12px;
}

.dialogue-pair {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}

.bubble {
  font-size: 14px;
  line-height: 1.6;
  padding: 10px 14px;
  border-radius: 14px;
  max-width: 88%;
}

.bubble .who {
  display: block;
  font-size: 11px;
  opacity: 0.7;
  margin-bottom: 3px;
}

.bubble.user {
  align-self: flex-end;
  background: #54a3f8;
  color: #fff;
}

.bubble.assistant {
  align-self: flex-start;
  background: #f3f5f8;
  color: #2a2a2a;
}

.prompt-block {
  border-top: 1px dashed #ddd;
  padding-top: 14px;
}

.prompt-block summary {
  font-size: 13px;
  color: #54a3f8;
  cursor: pointer;
  list-style: none;
}

.prompt-block summary::-webkit-details-marker { display: none; }

.prompt-block pre {
  margin-top: 12px;
  padding: 14px;
  background: #fafafa;
  border: 1px solid #eee;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.7;
  color: #333;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow-y: auto;
}

.copy-btn {
  margin-top: 12px;
  border: 1px solid #54a3f8;
  background: transparent;
  color: #54a3f8;
  border-radius: 14px;
  padding: 7px 16px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.copy-btn:hover {
  background: #54a3f8;
  color: #fff;
}
</style>
