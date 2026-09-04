<script setup lang="ts">
type NotificationItem = {
  id: number
  type: string
  title: string
  content: string | null
  linkUrl: string | null
  status: 'unread' | 'read'
  createdAt: string
  readAt: string | null
}

type NotificationPage = {
  list: NotificationItem[]
  total: number
  page: number
  size: number
}

const auth = useAuthStore()
const { request } = useApi()
const statusOptions = [
  { label: '全部', value: '' },
  { label: '未读', value: 'unread' },
  { label: '已读', value: 'read' }
]

const activeStatus = ref('')
const notifications = ref<NotificationItem[]>([])
const total = ref(0)
const loading = ref(false)
const acting = ref(false)
const errorMessage = ref('')

onMounted(async () => {
  if (!auth.user) {
    await navigateTo('/login')
    return
  }
  await loadNotifications()
})

async function loadNotifications() {
  loading.value = true
  errorMessage.value = ''
  try {
    const query = new URLSearchParams({
      page: '1',
      size: '30'
    })
    if (activeStatus.value) {
      query.set('status', activeStatus.value)
    }
    const data = await request<NotificationPage>(`/notification?${query.toString()}`)
    notifications.value = data.list
    total.value = data.total
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
}

async function selectStatus(status: string) {
  activeStatus.value = status
  await loadNotifications()
}

async function markRead(item: NotificationItem) {
  if (item.status === 'read' || acting.value) return
  acting.value = true
  errorMessage.value = ''
  try {
    await request<NotificationItem>(`/notification/${item.id}/read`, { method: 'POST' })
    item.status = 'read'
    item.readAt = new Date().toISOString()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    acting.value = false
  }
}

async function markAllRead() {
  if (acting.value) return
  acting.value = true
  errorMessage.value = ''
  try {
    await request<{ count: number }>('/notification/read-all', { method: 'POST' })
    await loadNotifications()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    acting.value = false
  }
}

function typeLabel(type: string) {
  return ({
    comment: '评论',
    vote: '投票',
    report_created: '举报',
    report_handled: '处理结果',
    system: '系统'
  } as Record<string, string>)[type] || type
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}
</script>

<template>
  <main class="page">
    <section class="shell">
      <header class="topbar">
        <div>
          <p class="eyebrow">Notifications</p>
          <h1>通知</h1>
        </div>
        <nav class="nav-actions">
          <button class="button button-secondary" :disabled="loading" @click="loadNotifications">刷新</button>
          <button class="button button-primary" :disabled="acting" @click="markAllRead">全部已读</button>
        </nav>
      </header>

      <div class="filters">
        <button
          v-for="option in statusOptions"
          :key="option.value || 'all'"
          class="chip"
          :class="{ active: activeStatus === option.value }"
          @click="selectStatus(option.value)"
        >
          {{ option.label }}
        </button>
      </div>

      <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

      <section class="notification-list">
        <article v-if="loading" class="notification-card muted">加载通知中...</article>
        <article v-else-if="notifications.length === 0" class="notification-card muted">
          暂时没有通知。
        </article>

        <article
          v-for="item in notifications"
          :key="item.id"
          class="notification-card"
          :class="{ unread: item.status === 'unread' }"
        >
          <div class="card-main">
            <div class="card-head">
              <span class="badge">{{ typeLabel(item.type) }}</span>
              <span>{{ formatTime(item.createdAt) }}</span>
            </div>
            <h2>{{ item.title }}</h2>
            <p>{{ item.content || '暂无内容' }}</p>
          </div>
          <div class="card-actions">
            <NuxtLink
              v-if="item.linkUrl"
              class="button button-secondary"
              :to="item.linkUrl"
              @click="markRead(item)"
            >
              查看
            </NuxtLink>
            <button
              class="button"
              :class="item.status === 'unread' ? 'button-primary' : 'button-secondary'"
              type="button"
              :disabled="item.status === 'read' || acting"
              @click="markRead(item)"
            >
              {{ item.status === 'read' ? '已读' : '标为已读' }}
            </button>
          </div>
        </article>
      </section>

      <p class="summary">共 {{ total }} 条通知</p>
    </section>
  </main>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

.shell {
  width: min(100%, 980px);
  min-height: 100vh;
  margin: 0 auto;
  padding: 32px 24px;
}

.topbar,
.nav-actions,
.filters,
.card-head,
.card-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.topbar {
  justify-content: space-between;
  padding-bottom: 20px;
  border-bottom: 1px solid #deded8;
}

.nav-actions,
.card-actions {
  justify-content: flex-end;
}

.eyebrow {
  margin: 0 0 6px;
  color: #66665f;
  font-size: 14px;
  font-weight: 600;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  color: #181816;
  font-size: 32px;
  line-height: 1.2;
}

.filters {
  margin-top: 24px;
}

.chip {
  border: 1px solid #d6d6cf;
  border-radius: 999px;
  padding: 8px 13px;
  color: #4b4b46;
  background: #fff;
  cursor: pointer;
}

.chip.active {
  color: #fff;
  background: #1f1f1c;
  border-color: #1f1f1c;
}

.notification-list {
  display: grid;
  gap: 12px;
  margin-top: 24px;
}

.notification-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  border: 1px solid #deded8;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

.notification-card.unread {
  border-color: #b8b8ad;
  background: #fbfbf7;
}

.card-main {
  min-width: 0;
}

.card-head {
  justify-content: space-between;
  color: #686861;
  font-size: 13px;
}

.badge {
  border-radius: 999px;
  padding: 3px 8px;
  color: #4b4b46;
  background: #f1f1ed;
}

.notification-card h2 {
  margin-top: 10px;
  color: #181816;
  font-size: 18px;
}

.notification-card p {
  margin-top: 8px;
  color: #4b4b46;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.summary,
.muted {
  color: #686861;
}

.summary {
  margin-top: 18px;
}

@media (max-width: 760px) {
  .topbar,
  .notification-card {
    align-items: flex-start;
    grid-template-columns: 1fr;
  }

  .topbar {
    flex-direction: column;
  }

  .card-actions {
    justify-content: flex-start;
  }
}
</style>
