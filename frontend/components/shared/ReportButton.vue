<script setup lang="ts">
const props = defineProps<{
  targetType: 'topic' | 'debate' | 'comment' | 'user'
  targetId: number
  label?: string
}>()

const auth = useAuthStore()
const { request } = useApi()

const open = ref(false)
const reason = ref('')
const submitting = ref(false)
const message = ref('')
const errorMessage = ref('')

async function toggle() {
  if (!auth.user) {
    await navigateTo('/login')
    return
  }

  open.value = !open.value
  message.value = ''
  errorMessage.value = ''
}

async function submitReport() {
  if (!reason.value.trim() || submitting.value) return

  submitting.value = true
  message.value = ''
  errorMessage.value = ''

  try {
    await request('/report', {
      method: 'POST',
      body: JSON.stringify({
        targetType: props.targetType,
        targetId: props.targetId,
        reason: reason.value
      })
    })
    reason.value = ''
    open.value = false
    message.value = '举报已提交，管理员会在后台处理。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="report-box">
    <button class="report-trigger" type="button" :disabled="submitting" @click="toggle">
      {{ label || '举报' }}
    </button>

    <form v-if="open" class="report-form" @submit.prevent="submitReport">
      <textarea
        v-model="reason"
        maxlength="200"
        rows="3"
        placeholder="请简单说明举报原因"
      />
      <div class="report-actions">
        <span>{{ reason.length }}/200</span>
        <button class="button button-secondary" type="button" :disabled="submitting" @click="open = false">取消</button>
        <button class="button button-primary" type="submit" :disabled="submitting || !reason.trim()">
          {{ submitting ? '提交中' : '提交' }}
        </button>
      </div>
    </form>

    <p v-if="message" class="report-message">{{ message }}</p>
    <p v-if="errorMessage" class="report-error">{{ errorMessage }}</p>
  </div>
</template>

<style scoped>
.report-box {
  display: grid;
  gap: 8px;
}

.report-trigger {
  border: 1px solid #d6d6cf;
  border-radius: 7px;
  padding: 7px 10px;
  color: #4b4b46;
  background: #fff;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}

.report-trigger:disabled {
  cursor: wait;
  opacity: 0.72;
}

.report-form {
  display: grid;
  gap: 8px;
  width: min(100%, 420px);
  border: 1px solid #deded8;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.report-form textarea {
  width: 100%;
  resize: vertical;
  border: 1px solid #c8c8c0;
  border-radius: 7px;
  padding: 10px;
  color: #262622;
  outline: none;
  line-height: 1.6;
}

.report-form textarea:focus {
  border-color: #33332f;
}

.report-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.report-actions span {
  margin-right: auto;
  color: #686861;
  font-size: 13px;
}

.report-message,
.report-error {
  margin: 0;
  font-size: 13px;
}

.report-message {
  color: #166534;
}

.report-error {
  color: #9f1239;
}
</style>
