<template>
  <Transition name="fade">
    <div v-if="visible" class="report-mask" @click.self="$emit('close')">
      <div class="report-card">
        <h3 class="title">举报内容</h3>
        <p class="subtitle">请选择举报原因，我们会在审核后处理</p>

        <div class="reasons">
          <button
            v-for="r in REPORT_REASONS"
            :key="r"
            type="button"
            :class="['reason', { active: reason === r }]"
            @click="reason = r"
          >{{ r }}</button>
        </div>

        <textarea
          v-model.trim="detail"
          rows="3"
          maxlength="500"
          placeholder="补充说明（选填）"
          class="detail"
        ></textarea>

        <p v-if="error" class="error">{{ error }}</p>

        <div class="actions">
          <button class="ghost-btn" type="button" @click="$emit('close')">取消</button>
          <button class="primary-btn" type="button" :disabled="submitting" @click="submit">
            {{ submitting ? '提交中…' : '提交举报' }}
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, watch } from 'vue';
import { reportApi, REPORT_REASONS } from '@/api/reports';

const props = defineProps({
  visible: { type: Boolean, default: false },
  targetType: { type: String, required: true },
  targetId: { type: [Number, String], required: true }
});

const emit = defineEmits(['close', 'done']);

const reason = ref(REPORT_REASONS[0]);
const detail = ref('');
const submitting = ref(false);
const error = ref('');

// 每次打开重置状态
watch(() => props.visible, (v) => {
  if (v) {
    reason.value = REPORT_REASONS[0];
    detail.value = '';
    error.value = '';
  }
});

async function submit() {
  if (!localStorage.getItem('token')) {
    error.value = '请先登录后再举报';
    return;
  }
  submitting.value = true;
  error.value = '';
  try {
    await reportApi.submit({
      targetType: props.targetType,
      targetId: Number(props.targetId),
      reason: reason.value,
      detail: detail.value
    });
    emit('done');
    emit('close');
  } catch (e) {
    error.value = e.message || '举报失败';
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.report-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 20px;
}

.report-card {
  background: #fff;
  border-radius: 20px;
  padding: 24px;
  width: 100%;
  max-width: 420px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
}

.title {
  font-size: 18px;
  color: #1f1f1f;
  margin-bottom: 6px;
}

.subtitle {
  font-size: 13px;
  color: #8a8a8a;
  margin-bottom: 18px;
}

.reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.reason {
  padding: 7px 14px;
  border: 1px solid #e2e2e2;
  background: #fff;
  border-radius: 14px;
  font-size: 13px;
  color: #555;
  cursor: pointer;
  transition: all 0.2s;
}

.reason:hover { border-color: #54a3f8; color: #54a3f8; }

.reason.active {
  border-color: #54a3f8;
  background: rgba(84, 163, 248, 0.12);
  color: #2f6fb5;
  font-weight: 600;
}

.detail {
  width: 100%;
  padding: 10px 13px;
  border: 2px solid #e2e2e2;
  border-radius: 12px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.6;
  outline: none;
  resize: vertical;
  box-sizing: border-box;
  margin-bottom: 14px;
}

.detail:focus { border-color: #54a3f8; }

.error {
  font-size: 13px;
  color: #c62828;
  background: #ffebee;
  border-radius: 10px;
  padding: 8px 12px;
  margin-bottom: 14px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.primary-btn {
  background: #54a3f8;
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 10px 22px;
  font-size: 14px;
  cursor: pointer;
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
}

.ghost-btn:hover { border-color: #54a3f8; color: #54a3f8; }

.fade-enter-active,
.fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }
</style>
