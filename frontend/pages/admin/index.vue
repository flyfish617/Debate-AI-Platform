<script setup lang="ts">
type PageResponse<T> = {
  list: T[]
  total: number
  page: number
  size: number
}

type AdminReport = {
  id: number
  reporterId: number
  reporterUsername: string
  targetType: string
  targetId: number
  targetLabel: string
  targetStatus: string
  reason: string
  status: string
  createdAt: string
  updatedAt: string
}

type AdminUser = {
  id: number
  username: string
  email: string
  role: string
  status: string
  points: number
  wins: number
  losses: number
  createdAt: string
  updatedAt: string
}

const auth = useAuthStore()
const { request } = useApi()

const reportStatus = ref('pending')
const reports = ref<AdminReport[]>([])
const users = ref<AdminUser[]>([])
const reportTotal = ref(0)
const userTotal = ref(0)
const loadingReports = ref(false)
const loadingUsers = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const reportStatusOptions = [
  { label: '待处理', value: 'pending' },
  { label: '已处理', value: 'resolved' },
  { label: '已驳回', value: 'rejected' }
]

const isAdmin = computed(() => auth.user?.role === 'admin')

onMounted(async () => {
  if (!auth.user) {
    await navigateTo('/login')
    return
  }

  await auth.refreshMe()
  if (!isAdmin.value) {
    errorMessage.value = '需要管理员权限'
    return
  }

  await Promise.all([loadReports(), loadUsers()])
})

async function loadReports() {
  loadingReports.value = true
  errorMessage.value = ''
  try {
    const data = await request<PageResponse<AdminReport>>(`/admin/reports?page=1&size=20&status=${reportStatus.value}`)
    reports.value = data.list
    reportTotal.value = data.total
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loadingReports.value = false
  }
}

async function loadUsers() {
  loadingUsers.value = true
  errorMessage.value = ''
  try {
    const data = await request<PageResponse<AdminUser>>('/admin/users?page=1&size=20')
    users.value = data.list
    userTotal.value = data.total
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loadingUsers.value = false
  }
}

async function handleReport(report: AdminReport, action: 'moderate' | 'resolve' | 'reject') {
  successMessage.value = ''
  errorMessage.value = ''
  try {
    await request<AdminReport>(`/admin/reports/${report.id}/handle`, {
      method: 'POST',
      body: JSON.stringify({ action })
    })
    successMessage.value = action === 'reject' ? '已驳回举报' : '已处理举报'
    await Promise.all([loadReports(), loadUsers()])
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  }
}

async function deleteTopic(report: AdminReport) {
  if (report.targetType !== 'topic') return
  const confirmed = window.confirm(`确定删除话题“${report.targetLabel || `#${report.targetId}`}”吗？删除后话题广场和关联辩论大厅都不会再展示。`)
  if (!confirmed) return

  successMessage.value = ''
  errorMessage.value = ''
  try {
    await request<AdminReport>(`/admin/topics/${report.targetId}/delete`, {
      method: 'POST'
    })
    successMessage.value = '已删除话题'
    await Promise.all([loadReports(), loadUsers()])
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  }
}

function canDeleteTopic(report: AdminReport) {
  return report.targetType === 'topic' && report.targetStatus !== 'deleted' && report.targetStatus !== 'missing'
}

async function updateUserStatus(user: AdminUser, status: 'active' | 'disabled') {
  successMessage.value = ''
  errorMessage.value = ''
  try {
    await request<AdminUser>(`/admin/users/${user.id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status })
    })
    successMessage.value = status === 'active' ? '已启用用户' : '已禁用用户'
    await loadUsers()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  }
}

function targetTypeLabel(type: string) {
  return ({
    topic: '话题',
    debate: '辩论',
    comment: '评论',
    user: '用户'
  } as Record<string, string>)[type] || type
}
</script>

<template>
  <main class="page">
    <section class="shell">
      <header class="topbar">
        <div>
          <p class="eyebrow">Admin</p>
          <h1>管理后台</h1>
        </div>
        <nav class="nav-actions">
          <button class="button button-secondary" :disabled="loadingReports || !isAdmin" @click="loadReports">刷新举报</button>
          <button class="button button-secondary" :disabled="loadingUsers || !isAdmin" @click="loadUsers">刷新用户</button>
        </nav>
      </header>

      <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
      <p v-if="successMessage" class="form-success">{{ successMessage }}</p>

      <template v-if="isAdmin">
        <section class="toolbar" aria-label="举报状态">
          <button
            v-for="option in reportStatusOptions"
            :key="option.value"
            class="chip"
            :class="{ active: reportStatus === option.value }"
            @click="reportStatus = option.value; loadReports()"
          >
            {{ option.label }}
          </button>
        </section>

        <section class="section-block">
          <div class="section-head">
            <h2>举报处理</h2>
            <span>共 {{ reportTotal }} 条</span>
          </div>

          <article v-if="loadingReports" class="row muted">加载举报中...</article>
          <article v-else-if="reports.length === 0" class="row muted">当前没有举报。</article>

          <article v-for="report in reports" :key="report.id" class="row">
            <div class="row-main">
              <div class="meta">
                <span>{{ targetTypeLabel(report.targetType) }} #{{ report.targetId }}</span>
                <span>举报人：{{ report.reporterUsername }}</span>
                <span>状态：{{ report.status }}</span>
              </div>
              <h3>{{ report.targetLabel || '未命名对象' }}</h3>
              <p>{{ report.reason }}</p>
              <small>对象状态：{{ report.targetStatus }}</small>
            </div>
            <div v-if="report.status === 'pending'" class="row-actions">
              <button class="button button-primary" @click="handleReport(report, 'moderate')">处理并治理</button>
              <button class="button button-secondary" @click="handleReport(report, 'resolve')">仅标记处理</button>
              <button class="button button-secondary" @click="handleReport(report, 'reject')">驳回</button>
              <button
                v-if="canDeleteTopic(report)"
                class="button button-danger"
                type="button"
                @click="deleteTopic(report)"
              >
                删除话题
              </button>
            </div>
            <div v-else-if="canDeleteTopic(report)" class="row-actions">
              <button class="button button-danger" type="button" @click="deleteTopic(report)">删除话题</button>
            </div>
          </article>
        </section>

        <section class="section-block">
          <div class="section-head">
            <h2>用户管理</h2>
            <span>共 {{ userTotal }} 个用户</span>
          </div>

          <article v-if="loadingUsers" class="row muted">加载用户中...</article>
          <article v-else-if="users.length === 0" class="row muted">暂无用户。</article>

          <article v-for="user in users" :key="user.id" class="row compact">
            <div class="row-main">
              <div class="meta">
                <span>#{{ user.id }}</span>
                <span>{{ user.role }}</span>
                <span>{{ user.status }}</span>
              </div>
              <h3>{{ user.username }}</h3>
              <p>{{ user.email }}</p>
              <small>{{ user.points }} 分，{{ user.wins }} 胜 / {{ user.losses }} 负</small>
            </div>
            <div class="row-actions">
              <button
                v-if="user.status !== 'disabled'"
                class="button button-secondary"
                :disabled="user.id === auth.user?.id"
                @click="updateUserStatus(user, 'disabled')"
              >
                禁用
              </button>
              <button
                v-else
                class="button button-primary"
                @click="updateUserStatus(user, 'active')"
              >
                启用
              </button>
            </div>
          </article>
        </section>
      </template>
    </section>
  </main>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

.shell {
  width: min(100%, 1120px);
  min-height: 100vh;
  margin: 0 auto;
  padding: 32px 24px;
}

.topbar,
.nav-actions,
.toolbar,
.section-head,
.meta,
.row-actions {
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
.row-actions {
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
h3,
p {
  margin: 0;
}

h1 {
  color: #181816;
  font-size: 32px;
  line-height: 1.2;
}

.toolbar {
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

.section-block {
  margin-top: 26px;
}

.section-head {
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-head h2 {
  color: #181816;
  font-size: 22px;
}

.section-head span,
.meta,
small,
.muted {
  color: #686861;
}

.row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  border: 1px solid #deded8;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

.row + .row {
  margin-top: 12px;
}

.row.compact {
  align-items: center;
}

.row-main {
  min-width: 0;
}

.meta {
  font-size: 13px;
}

h3 {
  margin-top: 8px;
  color: #181816;
  font-size: 18px;
  overflow-wrap: anywhere;
}

.row p {
  margin-top: 8px;
  color: #4b4b46;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

small {
  display: block;
  margin-top: 8px;
}

.form-success {
  margin-top: 18px;
  color: #1d6b3a;
  font-weight: 700;
}

.button-danger {
  color: #fff;
  background: #9f1239;
  border-color: #9f1239;
}

@media (max-width: 760px) {
  .topbar,
  .row {
    align-items: flex-start;
    grid-template-columns: 1fr;
  }

  .topbar {
    flex-direction: column;
  }

  .row-actions {
    justify-content: flex-start;
  }
}
</style>
