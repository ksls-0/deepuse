<template>
  <div class="page">
    <AppNavbar :title="isEdit ? '编辑模板' : '新建模板'" back-to="/sets" />

    <main class="content">
      <p v-if="error" class="alert error">{{ error }}</p>

      <section class="card">
        <div class="field">
          <label>模板标题 <span class="req">*</span></label>
          <input v-model.trim="form.title" type="text" maxlength="120" placeholder="例如：如果让你描述自己" />
        </div>

        <div class="field">
          <label>简介</label>
          <textarea
            v-model.trim="form.description"
            rows="3"
            maxlength="500"
            placeholder="这组问题想了解什么？（选填）"
          ></textarea>
        </div>
      </section>

      <section class="card">
        <div class="questions-head">
          <h2>问题列表 <span class="count">{{ questions.length }}</span></h2>
          <button class="ghost-btn" @click="addQuestion">＋ 添加问题</button>
        </div>

        <p v-if="questions.length === 0" class="empty-tip">
          还没有问题，点右上角添加。问题顺序会影响 AI 生成时的参考顺序。
        </p>

        <div v-for="(q, index) in questions" :key="q.key" class="q-row">
          <span class="q-index">{{ index + 1 }}</span>
          <input
            v-model="q.content"
            type="text"
            maxlength="500"
            :placeholder="`第 ${index + 1} 个问题`"
            class="q-input"
          />
          <div class="q-ops">
            <button class="icon" :disabled="index === 0" title="上移" @click="move(index, -1)">↑</button>
            <button
              class="icon"
              :disabled="index === questions.length - 1"
              title="下移"
              @click="move(index, 1)"
            >↓</button>
            <button class="icon danger" title="删除" @click="removeQuestion(index)">✕</button>
          </div>
        </div>
      </section>

      <footer class="footer">
        <router-link to="/sets" class="ghost-btn">取消</router-link>
        <button class="primary-btn" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : isEdit ? '保存修改' : '创建并去填写' }}
        </button>
      </footer>
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

const isEdit = computed(() => !!route.params.id);

const form = reactive({ title: '', description: '' });
const questions = ref([]);
const error = ref('');
const saving = ref(false);
const loading = ref(false);

let seq = 1;
function newQuestion(content = '', id = null) {
  return { key: seq++, id, content };
}

function addQuestion() {
  questions.value.push(newQuestion());
}

function removeQuestion(index) {
  questions.value.splice(index, 1);
}

function move(index, delta) {
  const target = index + delta;
  if (target < 0 || target >= questions.value.length) return;
  const [item] = questions.value.splice(index, 1);
  questions.value.splice(target, 0, item);
}

async function load() {
  if (!isEdit.value) {
    questions.value = [newQuestion(), newQuestion(), newQuestion()];
    return;
  }
  loading.value = true;
  try {
    const detail = await setApi.detail(route.params.id);
    form.title = detail.title;
    form.description = detail.description || '';
    // 带上 id：保存时服务端按 id 做增量同步，已填写的答卷不会失效
    questions.value = (detail.questions || []).map((q) => newQuestion(q.content, q.id));
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function save() {
  error.value = '';

  if (!form.title) {
    error.value = '请填写模板标题';
    return;
  }
  // 保留 id：有 id 的是"原地修改"，没有的是"新增"，未出现的由服务端删除
  const payloadQuestions = questions.value
    .filter((q) => (q.content || '').trim())
    .map((q) => ({ id: q.id ?? null, content: q.content.trim() }));

  if (payloadQuestions.length === 0) {
    error.value = '请至少添加一个问题';
    return;
  }

  saving.value = true;
  try {
    const payload = { title: form.title, description: form.description, questions: payloadQuestions };
    const saved = isEdit.value
      ? await setApi.update(route.params.id, payload)
      : await setApi.create(payload);
    router.push(`/sets/${saved.id}/fill`);
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f3f6fa 0%, #ffffff 45%);
  padding-top: 60px;
  box-sizing: border-box;
}

.content {
  max-width: 760px;
  margin: 0 auto;
  padding: 32px 20px 80px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.card {
  background: #fff;
  border: 2px solid #ececec;
  border-radius: 20px;
  padding: 22px 24px;
}

.field { margin-bottom: 18px; }
.field:last-child { margin-bottom: 0; }

.field label {
  display: block;
  font-size: 13px;
  color: #555;
  margin-bottom: 8px;
}

.req { color: #ff4d4f; }

.field input,
.field textarea {
  width: 100%;
  padding: 11px 14px;
  border: 2px solid #e2e2e2;
  border-radius: 12px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
  resize: vertical;
  transition: border-color 0.2s;
}

.field input:focus,
.field textarea:focus { border-color: #54a3f8; }

.questions-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.questions-head h2 {
  font-size: 16px;
  color: #1f1f1f;
  display: flex;
  align-items: center;
  gap: 8px;
}

.count {
  font-size: 12px;
  color: #54a3f8;
  background: rgba(84, 163, 248, 0.12);
  border-radius: 10px;
  padding: 2px 9px;
}

.empty-tip {
  font-size: 13px;
  color: #999;
  padding: 12px 0;
}

.q-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.q-index {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: 50%;
  border: 2px solid #d5d5d5;
  color: #777;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.q-input {
  flex: 1;
  padding: 10px 12px;
  border: 2px solid #e2e2e2;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.q-input:focus { border-color: #54a3f8; }

.q-ops { display: flex; gap: 4px; }

.icon {
  width: 28px;
  height: 28px;
  border: 1px solid #e2e2e2;
  background: #fff;
  border-radius: 8px;
  color: #666;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.icon:hover:not(:disabled) { border-color: #54a3f8; color: #54a3f8; }
.icon.danger:hover { border-color: #ff4d4f; color: #ff4d4f; }
.icon:disabled { opacity: 0.35; cursor: not-allowed; }

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
  text-decoration: none;
  cursor: pointer;
  transition: all 0.2s;
}

.ghost-btn:hover { border-color: #54a3f8; color: #54a3f8; }

.alert {
  font-size: 13px;
  border-radius: 10px;
  padding: 10px 14px;
}

.alert.error { color: #c62828; background: #ffebee; }
</style>
