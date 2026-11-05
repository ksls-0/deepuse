<template>
  <div class="page">
    <AppNavbar :title="setTitle || '填写答卷'" back-to="/sets" />

    <main class="content">
      <div v-if="takedown" class="takedown-banner">
        <strong>该题集已被下架</strong>
        <span>原因：{{ takedown.reason || '未填写' }}</span>
        <span>下架后不会出现在广场，他人无法导入；你仍可继续编辑自己的内容。</span>
      </div>

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

        <!-- 步骤指示 -->
        <nav class="steps">
          <button
            :class="['step', { active: step === 1, done: step > 1 }]"
            type="button"
            @click="goStep(1)"
          >
            <span class="step-no">1</span>
            <span class="step-label">基础信息</span>
          </button>
          <span class="step-line"></span>
          <button
            :class="['step', { active: step === 2 }]"
            type="button"
            @click="goStep(2)"
          >
            <span class="step-no">2</span>
            <span class="step-label">问答</span>
          </button>
        </nav>

        <!-- 第一步：基础信息 -->
        <section v-show="step === 1" class="card">
          <h2 class="section-title">人物基础信息</h2>
          <p class="section-tip">
            这些属于「作答者」，同一题集不同人填写会得到不同角色。先设定人物，再去回答下面的问题。
          </p>

          <div class="field">
            <label>昵称 <span class="required">*</span></label>
            <input
              v-model.trim="form.personaName"
              type="text"
              maxlength="50"
              placeholder="这个角色叫什么"
            />
          </div>

          <div class="fields-head">
            <label class="fields-label">基础信息类别</label>
            <button class="add-btn" type="button" :disabled="personaFields.length >= 20" @click="addField">
              ＋ 添加一项
            </button>
          </div>
          <p class="fields-tip">
            除昵称外，其余信息由你自己定义类别，例如「职业 / 程序员」「年龄 / 28」「兴趣爱好 / 科幻小说」。
          </p>

          <div v-if="personaFields.length === 0" class="fields-empty">
            还没有自定义信息，点右上角「添加一项」开始
          </div>

          <div v-for="(f, i) in personaFields" :key="f.key" class="field-row">
            <input
              v-model.trim="f.label"
              type="text"
              maxlength="50"
              placeholder="类别，如 职业"
              class="label-input"
            />
            <input
              v-model.trim="f.value"
              type="text"
              maxlength="500"
              placeholder="内容，如 独立开发者"
              class="value-input"
            />
            <button class="row-del" type="button" title="删除这一项" @click="removeField(i)">✕</button>
          </div>

          <div class="field note-field">
            <label>补充说明</label>
            <textarea
              v-model.trim="form.note"
              rows="2"
              maxlength="1000"
              placeholder="任何想补充给 AI 的设定（选填）"
            ></textarea>
          </div>
        </section>

        <!-- 第二步：问答 -->
        <section v-show="step === 2" class="card">
          <div class="section-head">
            <h2 class="section-title">回答题集里的问题</h2>
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
          <button v-if="step === 2" class="ghost-btn" @click="goStep(1)">← 上一步</button>
          <button v-else class="ghost-btn" :disabled="saving" @click="save(false)">
            {{ saving ? '保存中…' : '保存草稿' }}
          </button>

          <button
            v-if="step === 1"
            class="primary-btn"
            :disabled="saving"
            @click="nextFromBasic"
          >下一步：回答问题 →</button>

          <template v-else>
            <button class="ghost-btn" :disabled="saving" @click="save(false)">
              {{ saving ? '保存中…' : '保存答卷' }}
            </button>
            <button class="primary-btn" :disabled="generating || saving" @click="save(true)">
              {{ generating ? 'AI 生成中，请稍候…' : '生成角色模板' }}
            </button>
          </template>
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
const takedown = ref(null);
const sheets = ref([]);
const currentSheetId = ref(null);
const items = ref([]);

/** 1 = 基础信息，2 = 问答 */
const step = ref(1);

const form = reactive({ personaName: '', note: '', label: '' });

/** 用户自定义的基础信息类别 */
const personaFields = ref([]);
let fieldSeq = 1;

const loading = ref(true);
const saving = ref(false);
const generating = ref(false);
const error = ref('');
const notice = ref('');

const answeredCount = computed(
  () => items.value.filter((i) => i.content && i.content.trim()).length
);

function addField() {
  if (personaFields.value.length >= 20) {
    error.value = '基础信息类别最多 20 项';
    return;
  }
  personaFields.value.push({ key: fieldSeq++, label: '', value: '' });
}

function removeField(index) {
  personaFields.value.splice(index, 1);
}

function goStep(target) {
  // 从第一步往前迈时要求先填昵称，保证「先设定人物」
  if (target === 2 && !form.personaName) {
    error.value = '请先填写人物昵称';
    step.value = 1;
    return;
  }
  error.value = '';
  step.value = target;
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function nextFromBasic() {
  goStep(2);
}

async function loadSheet(sheetId) {
  const sheet = await setApi.getSheet(route.params.id, sheetId);
  setTitle.value = sheet.setTitle || setTitle.value;
  currentSheetId.value = sheet.id || null;

  form.personaName = sheet.personaName || '';
  form.note = sheet.note || '';
  form.label = sheet.label || '';

  fieldSeq = 1;
  personaFields.value = (sheet.personaFields || []).map((f) => ({
    key: fieldSeq++,
    label: f.label || '',
    value: f.value || ''
  }));

  items.value = (sheet.answers || []).map((a) => ({
    questionId: a.questionId,
    question: a.question,
    content: a.content || ''
  }));

  // 已有内容时直接落在第一步，让用户先确认基础信息
  step.value = 1;
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
    takedown.value = detail.takenDown ? { reason: detail.takedownReason } : null;
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
    // 只提交填了类别名的行，空行直接丢弃
    personaFields: personaFields.value
      .filter((f) => f.label && f.label.trim())
      .map((f) => ({ label: f.label.trim(), value: (f.value || '').trim() })),
    note: form.note,
    label: form.label,
    answers: items.value.map((i) => ({ questionId: i.questionId, content: i.content }))
  };
}

async function save(thenGenerate) {
  error.value = '';
  notice.value = '';

  if (thenGenerate && !form.personaName) {
    error.value = '请先填写人物昵称';
    step.value = 1;
    return;
  }

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

/* ---------- 步骤条 ---------- */
.steps {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 6px;
}

.step {
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 4px;
}

.step-no {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid #d5d5d5;
  color: #999;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.step-label { font-size: 14px; color: #999; transition: all 0.2s; }

.step.active .step-no {
  border-color: #54a3f8;
  background: #54a3f8;
  color: #fff;
}

.step.active .step-label { color: #1f1f1f; font-weight: 600; }

.step.done .step-no {
  border-color: #54a3f8;
  color: #54a3f8;
}

.step.done .step-label { color: #2f6fb5; }

.step-line {
  flex: 1;
  height: 2px;
  background: #ececec;
  border-radius: 2px;
}

.section-title { font-size: 16px; color: #1f1f1f; margin-bottom: 6px; }

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

.field { margin-bottom: 14px; }
.field:last-child { margin-bottom: 0; }

.field label {
  display: block;
  font-size: 13px;
  color: #555;
  margin-bottom: 7px;
}

.required { color: #ff4d4f; }

.field input,
.field textarea,
.q-block textarea,
.field-row input {
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
.q-block textarea:focus,
.field-row input:focus { border-color: #54a3f8; }

.note-field { margin-top: 16px; }

/* ---------- 自定义基础信息 ---------- */
.fields-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20px;
  margin-bottom: 6px;
}

.fields-label { font-size: 13px; color: #555; }

.add-btn {
  background: transparent;
  border: 1px solid #54a3f8;
  color: #54a3f8;
  border-radius: 12px;
  padding: 5px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-btn:hover:not(:disabled) { background: #54a3f8; color: #fff; }
.add-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.fields-tip {
  font-size: 12px;
  color: #9a9a9a;
  line-height: 1.6;
  margin-bottom: 12px;
}

.fields-empty {
  font-size: 13px;
  color: #b5b5b5;
  padding: 14px;
  background: #fafafa;
  border: 1px dashed #e2e2e2;
  border-radius: 12px;
  text-align: center;
}

.field-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.label-input { flex: 0 0 34%; }
.value-input { flex: 1; }

.row-del {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border: 1px solid #e2e2e2;
  background: #fff;
  border-radius: 50%;
  color: #999;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.row-del:hover { border-color: #ff4d4f; color: #ff4d4f; }

/* ---------- 问答 ---------- */
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
  flex-wrap: wrap;
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

.takedown-banner {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #fff3f3;
  border: 1px solid #ffbdbd;
  border-left: 4px solid #ff4d4f;
  border-radius: 12px;
  padding: 14px 16px;
  font-size: 13px;
  color: #b23b3b;
  line-height: 1.6;
}

.takedown-banner strong { font-size: 14px; color: #c62828; }

.empty {
  text-align: center;
  color: #999;
  font-size: 14px;
  padding: 60px 0;
}

@media (max-width: 640px) {
  .field-row { flex-wrap: wrap; }
  .label-input { flex: 1 1 40%; }
  .value-input { flex: 1 1 40%; }
}
</style>
