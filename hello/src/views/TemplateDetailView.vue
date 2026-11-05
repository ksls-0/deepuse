<template>
  <div class="page">
    <AppNavbar :title="template.name || '模板详情'" back-to="/templates" />

    <main class="content">
      <p v-if="error" class="alert error">{{ error }}</p>
      <p v-if="notice" class="alert ok">{{ notice }}</p>
      <div v-if="loading" class="empty">加载中…</div>

      <template v-else-if="template.id">
        <div v-if="template.takenDown" class="takedown-banner">
          <strong>该模板已被下架</strong>
          <span>原因：{{ template.takedownReason || '未填写' }}</span>
          <span>下架后不会出现在模板广场，他人也无法使用；如有异议请联系管理员。</span>
        </div>

        <div class="toolbar">
          <span v-if="template.genModel === 'mock'" class="mock-badge">
            示例数据 · 配置 DEEPSEEK_API_KEY 后重新生成即为真实 AI 结果
          </span>
          <span v-else class="model-badge">由 {{ template.genModel }} 生成</span>

          <div class="toolbar-actions">
            <button class="ghost-btn" @click="toggleVisibility">
              {{ template.visibility === 1 ? '设为私密' : '公开到广场' }}
            </button>
            <button class="ghost-btn" @click="toggleEdit">{{ editing ? '取消编辑' : '编辑' }}</button>
            <button class="primary-btn" :disabled="starting" @click="startChat">
              {{ starting ? '创建中…' : '开始对话' }}
            </button>
          </div>
        </div>

        <CharacterCard v-if="!editing" :template="template" />

        <section v-else class="edit-card">
          <div class="field">
            <label>模板名称</label>
            <input v-model.trim="editForm.name" type="text" maxlength="80" />
          </div>
          <div class="field">
            <label>System Prompt（对话时实际注入的文本）</label>
            <textarea v-model="editForm.content" rows="14"></textarea>
          </div>
          <div class="edit-actions">
            <button class="ghost-btn" @click="toggleEdit">取消</button>
            <button class="primary-btn" :disabled="saving" @click="saveEdit">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
        </section>

        <p class="danger-zone">
          <button class="link-danger" @click="remove">删除这个模板</button>
        </p>
      </template>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppNavbar from '@/components/layout/AppNavbar.vue';
import CharacterCard from '@/components/CharacterCard.vue';
import { templateApi } from '@/api/templates';
import { conversationApi } from '@/api/conversations';

const route = useRoute();
const router = useRouter();

const template = reactive({ id: null });
const editForm = reactive({ name: '', content: '' });

const loading = ref(true);
const editing = ref(false);
const saving = ref(false);
const starting = ref(false);
const error = ref('');
const notice = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const data = await templateApi.detail(route.params.id);
    Object.assign(template, data);
    editForm.name = data.name || '';
    editForm.content = data.content || '';
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function toggleEdit() {
  if (editing.value) {
    editForm.name = template.name || '';
    editForm.content = template.content || '';
  }
  editing.value = !editing.value;
}

async function saveEdit() {
  if (!editForm.name) {
    error.value = '模板名称不能为空';
    return;
  }
  if (!editForm.content) {
    error.value = 'System Prompt 不能为空';
    return;
  }
  saving.value = true;
  error.value = '';
  notice.value = '';
  try {
    const updated = await templateApi.update(template.id, {
      name: editForm.name,
      content: editForm.content
    });
    Object.assign(template, updated);
    editing.value = false;
    notice.value = '已保存';
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}

async function toggleVisibility() {
  const next = template.visibility === 1 ? 0 : 1;
  error.value = '';
  notice.value = '';
  try {
    const updated = await templateApi.setVisibility(template.id, next);
    Object.assign(template, updated);
    notice.value = next === 1 ? '已公开到模板广场' : '已设为私密';
  } catch (e) {
    error.value = e.message;
  }
}

async function startChat() {
  starting.value = true;
  error.value = '';
  try {
    const conversation = await conversationApi.create(template.id);
    router.push(`/chat/${conversation.id}`);
  } catch (e) {
    error.value = e.message;
  } finally {
    starting.value = false;
  }
}

async function remove() {
  if (!confirm(`确定删除模板「${template.name}」吗？`)) return;
  try {
    await templateApi.remove(template.id);
    router.push('/templates');
  } catch (e) {
    error.value = e.message;
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
  padding: 28px 20px 60px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-actions { display: flex; gap: 10px; margin-left: auto; }

.model-badge,
.mock-badge {
  font-size: 12px;
  border-radius: 10px;
  padding: 4px 11px;
}

.model-badge {
  color: #2f6fb5;
  background: rgba(84, 163, 248, 0.12);
}

.mock-badge {
  color: #b26a00;
  background: #fff8e6;
  border: 1px solid #f0c36d;
}

.edit-card {
  background: #fff;
  border: 2px solid #ececec;
  border-radius: 20px;
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field label {
  display: block;
  font-size: 13px;
  color: #555;
  margin-bottom: 8px;
}

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
  line-height: 1.7;
}

.field input:focus,
.field textarea:focus { border-color: #54a3f8; }

.edit-actions { display: flex; justify-content: flex-end; gap: 10px; }

.primary-btn {
  background: #54a3f8;
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 10px 22px;
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
  padding: 8px 18px;
  font-size: 14px;
  color: #555;
  cursor: pointer;
  transition: all 0.2s;
}

.ghost-btn:hover { border-color: #54a3f8; color: #54a3f8; }

.danger-zone { text-align: right; }

.link-danger {
  background: none;
  border: none;
  color: #ff4d4f;
  font-size: 13px;
  cursor: pointer;
  text-decoration: underline;
}

.alert {
  font-size: 13px;
  border-radius: 10px;
  padding: 10px 14px;
}

.alert.error { color: #c62828; background: #ffebee; }
.alert.ok { color: #2e7d32; background: #e8f5e9; }

.empty { text-align: center; color: #999; font-size: 14px; padding: 60px 0; }
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
</style>
