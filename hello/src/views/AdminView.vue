<template>
  <div class="page">
    <AppNavbar title="内容审核" :show-back="false" />

    <main class="content">
      <header class="head">
        <div>
          <h1>内容审核</h1>
          <p class="hint">待处理 {{ stats.pendingReports }} 条</p>
        </div>
        <div class="tabs">
          <button :class="['tab', { active: tab === 'pending' }]" @click="switchTab('pending')">
            待处理
          </button>
          <button :class="['tab', { active: tab === 'handled' }]" @click="switchTab('handled')">
            已处理
          </button>
        </div>
      </header>

      <p v-if="error" class="alert error">{{ error }}</p>
      <p v-if="notice" class="alert ok">{{ notice }}</p>

      <div v-if="loading" class="empty">加载中…</div>

      <div v-else-if="items.length === 0" class="empty">
        {{ tab === 'pending' ? '没有待处理的举报，很干净' : '还没有处理记录' }}
      </div>

      <div v-else class="list">
        <article v-for="r in items" :key="r.id" class="card">
          <header class="card-head">
            <span :class="['type-tag', r.targetType === 'SET' ? 'set' : 'template']">
              {{ r.targetType === 'SET' ? '题集' : '模板' }}
            </span>
            <span class="target-title">{{ r.targetTitle }}</span>
            <span v-if="r.targetTakenDown" class="down-tag">已下架</span>
            <span :class="['status-tag', r.status.toLowerCase()]">{{ statusText(r.status) }}</span>
          </header>

          <dl class="meta">
            <div><dt>举报原因</dt><dd class="reason">{{ r.reason }}</dd></div>
            <div v-if="r.detail"><dt>补充说明</dt><dd>{{ r.detail }}</dd></div>
            <div><dt>举报人</dt><dd>{{ r.reporterAccount || '未知' }}</dd></div>
            <div><dt>内容作者</dt><dd>{{ r.targetOwnerAccount || '未知' }}</dd></div>
            <div><dt>举报时间</dt><dd>{{ formatTime(r.createdAt) }}</dd></div>
            <div v-if="r.handledAt"><dt>处理时间</dt><dd>{{ formatTime(r.handledAt) }}</dd></div>
            <div v-if="r.handleNote"><dt>处理备注</dt><dd>{{ r.handleNote }}</dd></div>
          </dl>

          <footer class="card-actions">
            <template v-if="r.status === 'PENDING'">
              <button class="ghost-btn" :disabled="busy === r.id" @click="reject(r)">举报不成立</button>
              <button class="danger-btn" :disabled="busy === r.id" @click="takeDown(r)">
                {{ busy === r.id ? '处理中…' : '下架并处理' }}
              </button>
            </template>
            <template v-else>
              <router-link
                v-if="r.targetType === 'SET'"
                :to="`/explore/sets/${r.targetId}`"
                class="ghost-btn"
              >查看内容</router-link>
              <router-link
                v-else
                :to="`/explore/templates/${r.targetId}`"
                class="ghost-btn"
              >查看内容</router-link>
              <button
                v-if="r.targetTakenDown"
                class="primary-btn"
                :disabled="busy === r.id"
                @click="restore(r)"
              >恢复内容</button>
            </template>
          </footer>
        </article>
      </div>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import AppNavbar from '@/components/layout/AppNavbar.vue';
import { adminApi } from '@/api/admin';

const tab = ref('pending');
const items = ref([]);
const loading = ref(true);
const error = ref('');
const notice = ref('');
const busy = ref(null);
const stats = reactive({ pendingReports: 0 });

function statusText(status) {
  if (status === 'PENDING') return '待处理';
  if (status === 'TAKEN_DOWN') return '已下架';
  if (status === 'REJECTED') return '已驳回';
  return status;
}

function formatTime(v) {
  return v ? new Date(v).toLocaleString() : '';
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [page, s] = await Promise.all([
      tab.value === 'pending' ? adminApi.pendingReports(0, 50) : adminApi.handledReports(0, 50),
      adminApi.stats()
    ]);
    items.value = page.items || [];
    stats.pendingReports = s.pendingReports || 0;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function switchTab(next) {
  if (tab.value === next) return;
  tab.value = next;
  notice.value = '';
  load();
}

async function takeDown(r) {
  const note = window.prompt('下架理由（会记录到处置结果里，可留空使用举报原因）', '');
  if (note === null) return;
  busy.value = r.id;
  error.value = '';
  notice.value = '';
  try {
    await adminApi.handleReport(r.id, 'TAKEDOWN', note || undefined);
    notice.value = '已下架该内容，同内容的其它举报也一并处理';
    await load();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = null;
  }
}

async function reject(r) {
  const note = window.prompt('驳回说明（选填）', '');
  if (note === null) return;
  busy.value = r.id;
  error.value = '';
  notice.value = '';
  try {
    await adminApi.handleReport(r.id, 'REJECT', note || undefined);
    notice.value = '已驳回该举报';
    await load();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = null;
  }
}

async function restore(r) {
  if (!confirm('确定恢复该内容吗？恢复后它会回到作者原来的公开状态。')) return;
  busy.value = r.id;
  error.value = '';
  notice.value = '';
  try {
    await adminApi.restore(r.targetType, r.targetId);
    notice.value = '内容已恢复';
    await load();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = null;
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
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 20px 60px;
}

.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 22px;
}

.head h1 { font-size: 24px; color: #1f1f1f; margin-bottom: 6px; }
.hint { font-size: 13px; color: #8a8a8a; }

.tabs {
  display: flex;
  gap: 6px;
  background: #eef2f7;
  border-radius: 16px;
  padding: 4px;
}

.tab {
  border: none;
  background: transparent;
  padding: 9px 22px;
  border-radius: 12px;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.tab.active {
  background: #fff;
  color: #1f1f1f;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.list { display: flex; flex-direction: column; gap: 14px; }

.card {
  background: #fff;
  border: 2px solid #ececec;
  border-radius: 18px;
  padding: 18px 20px;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.type-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  border: 1px solid currentColor;
}

.type-tag.set { color: #2f6fb5; }
.type-tag.template { color: #7a52c7; }

.target-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f1f1f;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.down-tag {
  font-size: 12px;
  color: #c62828;
  background: #ffebee;
  border-radius: 10px;
  padding: 2px 10px;
}

.status-tag {
  font-size: 12px;
  border-radius: 10px;
  padding: 2px 10px;
}

.status-tag.pending { color: #b26a00; background: #fff8e6; }
.status-tag.taken_down { color: #c62828; background: #ffebee; }
.status-tag.rejected { color: #666; background: #f0f0f0; }

.meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 8px 18px;
  margin-bottom: 14px;
}

.meta > div { display: flex; gap: 8px; font-size: 13px; }

.meta dt { color: #9a9a9a; flex-shrink: 0; }
.meta dd { color: #333; word-break: break-word; }
.meta dd.reason { color: #b23b3b; font-weight: 600; }

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px dashed #eee;
  padding-top: 14px;
}

.primary-btn {
  background: #54a3f8;
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 9px 20px;
  font-size: 14px;
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
}

.primary-btn:hover:not(:disabled) { background: #3d8be6; }
.primary-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.danger-btn {
  background: #ff4d4f;
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 9px 20px;
  font-size: 14px;
  cursor: pointer;
}

.danger-btn:hover:not(:disabled) { background: #e03b3d; }
.danger-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.ghost-btn {
  background: #fff;
  border: 2px solid #e2e2e2;
  border-radius: 14px;
  padding: 8px 18px;
  font-size: 14px;
  color: #555;
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
}

.ghost-btn:hover:not(:disabled) { border-color: #54a3f8; color: #54a3f8; }
.ghost-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.alert {
  font-size: 13px;
  border-radius: 10px;
  padding: 10px 14px;
  margin-bottom: 16px;
}

.alert.error { color: #c62828; background: #ffebee; }
.alert.ok { color: #2e7d32; background: #e8f5e9; }

.empty {
  text-align: center;
  color: #999;
  font-size: 14px;
  padding: 60px 0;
}
</style>
