<template>
  <div class="s-page">
    <SeniorHeader :title="setTitle || '回答问题'" back-to="/senior/pick" />

    <div class="s-content">
      <div v-if="loading" class="s-text">正在加载…</div>
      <p v-else-if="loadError" class="s-alert">{{ loadError }}</p>

      <template v-else>
        <!-- 进度 -->
        <div class="progress-row">
          <span class="s-muted">第 {{ stepIndex + 1 }} / {{ totalSteps }} 步</span>
          <div class="s-progress">
            <div class="s-progress-fill" :style="{ width: progressPercent + '%' }"></div>
          </div>
        </div>

        <!-- 第一步：人物名字 -->
        <template v-if="isNameStep">
          <h1 class="s-h1">这个角色叫什么名字？</h1>
          <p class="s-text">随便起个名字就行，比如「老王」「梅姨」。</p>
          <input
            v-model.trim="personaName"
            class="s-input"
            type="text"
            maxlength="50"
            placeholder="输入名字"
          />
        </template>

        <!-- 后面的步骤：一个问题一屏 -->
        <template v-else>
          <h1 class="s-h1">{{ currentQuestion.question }}</h1>
          <p class="s-muted">想到多少写多少，不想答也可以直接点「下一题」。</p>
          <textarea
            :ref="setTextareaRef"
            v-model="currentAnswer"
            class="s-textarea"
            rows="6"
            maxlength="2000"
            placeholder="在这里慢慢写…"
          ></textarea>
        </template>

        <p v-if="error" class="s-alert">{{ error }}</p>

        <!-- 生成中：整屏提示，避免老人以为没反应 -->
        <div v-if="generating" class="s-card waiting">
          <p class="s-h2">正在生成角色…</p>
          <p class="s-text">大概需要十几秒，请不要关掉页面。</p>
        </div>

        <div v-else class="actions">
          <button v-if="stepIndex > 0" class="s-btn s-btn-ghost" type="button" :disabled="saving" @click="prev">
            ← 上一步
          </button>
          <button class="s-btn s-btn-primary" type="button" :disabled="saving" @click="next">
            {{ isLastStep ? (saving ? '正在生成…' : '生成我的角色') : '下一步 →' }}
          </button>
        </div>

        <button class="s-btn s-btn-ghost" type="button" :disabled="saving || generating" @click="saveOnly">
          先保存，稍后再填
        </button>
        <p v-if="notice" class="s-alert s-alert-ok">{{ notice }}</p>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import SeniorHeader from '@/components/senior/SeniorHeader.vue';
import { setApi } from '@/api/sets';
import { conversationApi } from '@/api/conversations';

const route = useRoute();
const router = useRouter();

const setId = route.params.setId;

const loading = ref(true);
const loadError = ref('');
const saving = ref(false);
const generating = ref(false);
const error = ref('');
const notice = ref('');

const setTitle = ref('');
const personaName = ref('');
const sheetId = ref(null);
const questions = ref([]);
const answers = reactive({});

const stepIndex = ref(0);

const isNameStep = computed(() => stepIndex.value === 0);
const totalSteps = computed(() => questions.value.length + 1);
const isLastStep = computed(() => stepIndex.value === totalSteps.value - 1);
const progressPercent = computed(() =>
  Math.round(((stepIndex.value + 1) / Math.max(totalSteps.value, 1)) * 100)
);

const currentQuestion = computed(() => {
  const q = questions.value[stepIndex.value - 1];
  return q || { id: null, content: '' };
});

const currentAnswer = computed({
  get() {
    const q = currentQuestion.value;
    return q && q.id != null ? answers[q.id] || '' : '';
  },
  set(value) {
    const q = currentQuestion.value;
    if (q && q.id != null) answers[q.id] = value;
  }
});

function setTextareaRef(el) {
  if (el) el.focus();
}

async function load() {
  try {
    const [detail, sheet] = await Promise.all([
      setApi.detail(setId),
      setApi.getSheet(setId)
    ]);
    setTitle.value = detail.title || '';
    questions.value = (sheet.answers || []).map((a) => ({
      id: a.questionId,
      content: a.question || ''
    }));

    sheetId.value = sheet.id || null;
    personaName.value = sheet.personaName || '';
    (sheet.answers || []).forEach((a) => {
      answers[a.questionId] = a.content || '';
    });

    if (questions.value.length === 0 && (detail.questions || []).length > 0) {
      questions.value = detail.questions.map((q) => ({ id: q.id, content: q.content }));
    }
  } catch (e) {
    loadError.value = e.message;
  } finally {
    loading.value = false;
  }
}

function buildPayload() {
  return {
    personaName: personaName.value,
    personaFields: [],
    note: '',
    label: '',
    answers: questions.value.map((q) => ({
      questionId: q.id,
      content: answers[q.id] || ''
    }))
  };
}

async function prev() {
  error.value = '';
  notice.value = '';
  if (stepIndex.value > 0) stepIndex.value -= 1;
  await nextTick();
  if (window.scrollTo) window.scrollTo({ top: 0 });
}

async function next() {
  error.value = '';
  notice.value = '';

  if (isNameStep.value) {
    if (!personaName.value) {
      error.value = '请先给角色起个名字';
      return;
    }
  } else if (isLastStep.value) {
    await generate();
    return;
  }

  stepIndex.value += 1;
  await nextTick();
  if (window.scrollTo) window.scrollTo({ top: 0 });
}

async function saveOnly() {
  error.value = '';
  notice.value = '';
  saving.value = true;
  try {
    const saved = await setApi.saveSheet(setId, sheetId.value, buildPayload());
    sheetId.value = saved.id;
    notice.value = '已保存，下次进来还能接着填。';
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}

async function generate() {
  error.value = '';
  notice.value = '';
  saving.value = true;
  try {
    const saved = await setApi.saveSheet(setId, sheetId.value, buildPayload());
    sheetId.value = saved.id;

    generating.value = true;
    const template = await setApi.generate(setId, saved.id);

    // 生成完直接带他去聊天，省一步
    const conversation = await conversationApi.create(template.id);
    router.push(`/senior/chat/${conversation.id}`);
  } catch (e) {
    error.value = e.message;
  } finally {
    generating.value = false;
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.progress-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.actions {
  display: flex;
  gap: 14px;
}

.actions .s-btn {
  flex: 1 1 0;
}

.waiting {
  align-items: center;
  text-align: center;
}
</style>
