<template>
  <div class="page">
    <AppNavbar :title="setTitle || '填写答卷'" back-to="/sets" />

    <main class="content">
      <p v-if="error" class="alert error">{{ error }}</p>
      <p v-if="notice" class="alert ok">{{ notice }}</p>

      <div v-if="loading" class="empty">加载中…</div>

      <template v-else>
        <!-- 版本切换 -->
        <section class="card version-bar">
          <div class="version-left">
            <label>答卷版本</label>
            <select :value="currentSheetId || ''" @change="switchSheet($event.target.value)">
              <option value="">（新答卷，尚未保存）</option>
              <option v-for="s in sheets" :key="s.id" :value="s.id">
                {{ s.label || '未命名' }} · 已答 {{ s.answeredCount }} 题
              </option>
            </select>
          </div>
          <button class="ghost-btn" @click="createSheet">＋ 新建一份</button>
        </section>

        <!-- 角色基本信息 -->
        <section class="card">
          <h2 class="section-title">角色基本信息</h2>
          <p class="section-tip">这些属于“作答者”，同一题集不同人填写会得到不同角色。</p>

          <div class="grid-3">
            <div class="field">
              <label>角色名</label>
              <input v-model.trim="form.personaName" type="text" maxlength="50" placeholder="选填" />
            </div>
            <div class="field">
              <label>年龄</label>
              <input v-model.number="form.personaAge" type="number" min="0" max="300" placeholder="选填" />
            </div>
            <div class="field">
              <label>职业</label>
              <input v-model.trim="form.personaOccupation" type="text" maxlength="50" placeholder="选填" />
            </div>
          </div>

          <div class="field">
            <label>补充说明</label>
            <textarea
              v-model.trim="form.note"
              rows="2"
              maxlength="1000"
              placeholder="任何想补充给 AI 的设定（选填）"
            ></textarea>
          </div>
        </section>

        <!-- 问题作答 -->
        <section class="card">
          <div class="section-head">
            <h2 class="section-title">回答问题</h2>
            <span class="answered">{{ answeredCount }} / {{ items.length }} 已作答</span>
          </div>
          <p class="section-tip">留空的问题不会进入 AI 的角色设定，建议至少答 3 题。</p>

          <div v-for="(item, index) in items" :key="item.questionId" class="q-block">
            <div class="q-title">
              <span class="q-index">{{ index + 1 }}</span>
              <span>{{ item.question }}</span>
            </div>
            <textarea
              v-model="item.content"
              rows="2"
              maxlength="2000"
              placeholder="你的回答…"
            ></textarea>
          </div>
        </section>

        <footer class="footer">
          <button class="ghost-btn" :disabled="saving" @click="save(false)">
            {{ saving ? '保存中…' : '保存答卷' }}
          </button>
          <button class="primary-btn" :disabled="generating || saving" @click="save(true)">
            {{ generating ? 'AI 生成中，请稍候…' : '生成角色模板' }}
          </button>
        </footer>

        <p class="generate-tip">
          生成会先保存答卷，再调用 DeepSeek 产出结构化角色卡。通常需要 15~60 秒。
        </p>
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppNavbar from '@/components/layout/AppNavbar.vue';
import { setApi } from '@/api/sets';

const route = useRoute();
const router = useRouter();

const setTitle = ref('');
const sheets = ref([]);
const currentSheetId = ref(null);
const items = ref([]);

const form = reactive({
  personaName: '',
  personaAge: null,
  personaOccupation: '',
  note: '',
  label: ''
});

const loading = ref(true);
const saving = ref(false);
const generating = ref(false);
const error = ref('');
const notice = ref('');

const answeredCount = computed(
  () => items.value.filter((i) => i.content && i.content.trim()).length
);

async function loadSheet(sheetId) {
  const sheet = await setApi.getSheet(route.params.id, sheetId);
  setTitle.value = sheet.setTitle || setTitle.value;
  currentSheetId.value = sheet.id || null;

  form.personaName = sheet.personaName || '';
  form.personaAge = sheet.personaAge ?? null;
  form.personaOccupation = sheet.personaOccupation || '';
  form.note = sheet.note || '';
  form.label = sheet.label || '';

  items.value = (sheet.answers || []).map((a) => ({
    questionId: a.questionId,
    question: a.question,
    content: a.content || ''
  }));
}

async function loadAll() {
  loading.value = true;
  error.value = '';
  try {
    const [detail, sheetList] = await Promise.all([
      setApi.detail(route.params.id),
      setApi.listSheets(route.params.id)
    ]);
    setTitle.value = detail.title;
    sheets.value = sheetList || [];

    const querySheetId = route.query.sheetId ? Number(route.query.sheetId) : null;
    await loadSheet(querySheetId);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function switchSheet(value) {
  error.value = '';
  try {
    await loadSheet(value ? Number(value) : null);
  } catch (e) {
    error.value = e.message;
  }
}

async function createSheet() {
  error.value = '';
  try {
    const created = await setApi.createSheet(route.params.id);
    sheets.value = await setApi.listSheets(route.params.id);
    await loadSheet(created.id);
    notice.value = '已新建一份空白答卷';
  } catch (e) {
    error.value = e.message;
  }
}

function buildPayload() {
  return {
    personaName: form.personaName,
    personaAge: form.personaAge === '' ? null : form.personaAge,
    personaOccupation: form.personaOccupation,
    note: form.note,
    label: form.label,
    answers: items.value.map((i) => ({ questionId: i.questionId, content: i.content }))
  };
}

async function save(thenGenerate) {
  error.value = '';
  notice.value = '';
  saving.value = true;
  try {
    const saved = await setApi.saveSheet(route.params.id, currentSheetId.value, buildPayload());
    currentSheetId.value = saved.id;
    sheets.value = await setApi.listSheets(route.params.id);

    if (!thenGenerate) {
      notice.value = '答卷已保存';
      return;
    }

    if (answeredCount.value === 0) {
      error.value = '请至少回答一个问题再生成模板';
      return;
    }

    generating.value = true;
    const template = await setApi.generate(route.params.id, saved.id);
    router.push(`/templates/${template.id}`);
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
    generating.value = false;
  }
}

onMounted(loadAll);
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f3f6fa 0%, #ffffff 45%);
  padding-top: 60px;
  box-sizing: border-box;
}

.content {
  max-width: 780px;
  margin: 0 auto;
  padding: 32px 20px 80px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card {
  background: #fff;
  border: 2px solid #ececec;
  border-radius: 20px;
  padding: 22px 24px;
}

.version-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.version-left { display: flex; flex-direction: column; gap: 8px; }

.version-left label { font-size: 13px; color: #555; }

.version-left select {
  padding: 9px 12px;
  border: 2px solid #e2e2e2;
  border-radius: 10px;
  font-size: 13px;
  outline: none;
  min-width: 260px;
  background: #fff;
}

.section-title {
  font-size: 16px;
  color: #1f1f1f;
  margin-bottom: 6px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.answered {
  font-size: 12px;
  color: #54a3f8;
  background: rgba(84, 163, 248, 0.12);
  border-radius: 10px;
  padding: 3px 10px;
}

.section-tip {
  font-size: 12px;
  color: #9a9a9a;
  margin-bottom: 18px;
  line-height: 1.6;
}

.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}

.field { margin-bottom: 14px; }
.field:last-child { margin-bottom: 0; }

.field label {
  display: block;
  font-size: 13px;
  color: #555;
  margin-bottom: 7px;
}

.field input,
.field textarea,
.q-block textarea {
  width: 100%;
  padding: 10px 13px;
  border: 2px solid #e2e2e2;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
  resize: vertical;
  transition: border-color 0.2s;
}

.field input:focus,
.field textarea:focus,
.q-block textarea:focus { border-color: #54a3f8; }

.q-block { margin-bottom: 18px; }
.q-block:last-child { margin-bottom: 0; }

.q-title {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 14px;
  color: #2a2a2a;
  line-height: 1.5;
  margin-bottom: 8px;
}

.q-index {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #54a3f8;
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
}

.footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.primary-btn {
  background: #54a3f8;
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 11px 26px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}

.primary-btn:hover:not(:disabled) { background: #3d8be6; }
.primary-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.ghost-btn {
  background: #fff;
  border: 2px solid #e2e2e2;
  border-radius: 14px;
  padding: 9px 20px;
  font-size: 14px;
  color: #555;
  cursor: pointer;
  transition: all 0.2s;
}

.ghost-btn:hover:not(:disabled) { border-color: #54a3f8; color: #54a3f8; }
.ghost-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.generate-tip {
  font-size: 12px;
  color: #9a9a9a;
  text-align: right;
}

.alert {
  font-size: 13px;
  border-radius: 10px;
  padding: 10px 14px;
}

.alert.error { color: #c62828; background: #ffebee; }
.alert.ok { color: #2e7d32; background: #e8f5e9; }

.empty {
  text-align: center;
  color: #999;
  font-size: 14px;
  padding: 60px 0;
}

@media (max-width: 640px) {
  .grid-3 { grid-template-columns: 1fr; }
}
</style>
