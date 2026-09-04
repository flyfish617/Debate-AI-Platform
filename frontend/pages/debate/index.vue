<script setup lang="ts">
type Topic = {
  id: number
  title: string
  description: string | null
  category: string
}

type DebateSummary = {
  id: number
  topic: Topic
  userId: number
  userStance: 'pro' | 'con'
  aiStance: 'pro' | 'con'
  aiModel: string
  style: 'mild' | 'intense'
  visibility: 'public' | 'private'
  status: string
  currentRound: number
  maxRounds: number
  winner: 'user' | 'ai' | 'draw' | null
  userVoteCount: number
  aiVoteCount: number
  createdAt: string
  endedAt: string | null
}

type DebatePage = {
  list: DebateSummary[]
  total: number
  page: number
  size: number
}

const statusOptions = [
  { label: '全部', value: '' },
  { label: '进行中', value: 'active' },
  { label: '结辩中', value: 'ending' },
  { label: '已结束', value: 'ended' }
]

const debates = ref<DebateSummary[]>([])
const total = ref(0)
const activeStatus = ref('')
const loading = ref(false)
const errorMessage = ref('')

const { request } = useApi()

onMounted(loadDebates)

async function loadDebates() {
  loading.value = true
  errorMessage.value = ''

  try {
    const query = new URLSearchParams({
      page: '1',
      size: '20'
    })
    if (activeStatus.value) {
      query.set('status', activeStatus.value)
    }
    const data = await request<DebatePage>(`/debate?${query.toString()}`)
    debates.value = data.list
    total.value = data.total
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
}

async function selectStatus(status: string) {
  activeStatus.value = status
  await loadDebates()
}

function stanceLabel(stance: 'pro' | 'con') {
  return stance === 'pro' ? '正方' : '反方'
}

function styleLabel(style: 'mild' | 'intense') {
  return style === 'mild' ? '温和' : '激烈'
}

function statusLabel(status: string) {
  if (status === 'active') return '进行中'
  if (status === 'ending') return '结辩生成中'
  if (status === 'ended') return '已结束'
  if (status === 'failed') return '结辩失败'
  return status
}

function winnerLabel(winner: DebateSummary['winner']) {
  if (!winner) return '待定'
  return winner === 'user' ? '用户胜' : winner === 'ai' ? 'AI 胜' : '平局'
}
</script>

<template>
  <main class="page">
    <section class="shell">
      <header class="topbar">
        <div>
          <h1>辩论大厅</h1>
        </div>
        <nav class="nav-actions">
          <button class="button button-secondary" :disabled="loading" @click="loadDebates">刷新</button>
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

      <section class="debate-list">
        <article v-if="loading" class="debate-card muted">加载中...</article>
        <article v-else-if="debates.length === 0" class="debate-card muted">
          暂时没有公开辩论。可以先去话题广场发起一场。
        </article>

        <article v-for="debate in debates" :key="debate.id" class="debate-card">
          <div class="card-head">
            <span class="badge">{{ debate.topic.category }}</span>
            <span>{{ statusLabel(debate.status) }}</span>
          </div>
          <h2>{{ debate.topic.title }}</h2>
          <p>{{ debate.topic.description || '暂无描述' }}</p>

          <dl class="meta-grid">
            <div>
              <dt>用户立场</dt>
              <dd>{{ stanceLabel(debate.userStance) }}</dd>
            </div>
            <div>
              <dt>AI 立场</dt>
              <dd>{{ stanceLabel(debate.aiStance) }}</dd>
            </div>
            <div>
              <dt>风格</dt>
              <dd>{{ styleLabel(debate.style) }}</dd>
            </div>
            <div>
              <dt>轮次</dt>
              <dd>{{ debate.currentRound }} / {{ debate.maxRounds }}</dd>
            </div>
            <div>
              <dt>结果</dt>
              <dd>{{ winnerLabel(debate.winner) }}</dd>
            </div>
            <div>
              <dt>票数</dt>
              <dd>{{ debate.userVoteCount }} : {{ debate.aiVoteCount }}</dd>
            </div>
          </dl>

          <div class="actions">
            <NuxtLink class="button button-primary" :to="`/debate/${debate.id}`">围观辩论</NuxtLink>
            <NuxtLink class="button button-secondary" :to="`/topic/${debate.topic.id}`">查看话题</NuxtLink>
          </div>
        </article>
      </section>

      <p class="summary">共 {{ total }} 场公开辩论</p>
    </section>
  </main>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

.shell {
  width: min(100%, 1100px);
  min-height: 100vh;
  margin: 0 auto;
  padding: 32px 24px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid #deded8;
}

h1,
h2 {
  margin: 0;
  color: #181816;
}

h1 {
  font-size: 32px;
  line-height: 1.2;
}

h2 {
  margin-top: 14px;
  font-size: 20px;
}

.nav-actions,
.filters,
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.nav-actions {
  justify-content: flex-end;
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

.debate-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 24px;
}

.debate-card {
  border: 1px solid #deded8;
  border-radius: 8px;
  padding: 18px;
  background: #fff;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #686861;
  font-size: 13px;
}

.badge {
  border-radius: 999px;
  padding: 3px 8px;
  color: #4b4b46;
  background: #f1f1ed;
}

.debate-card p {
  min-height: 52px;
  margin: 10px 0 18px;
  color: #4b4b46;
  line-height: 1.7;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.meta-grid div {
  border-top: 1px solid #eeeeea;
  padding-top: 10px;
}

.meta-grid dt {
  color: #686861;
  font-size: 12px;
}

.meta-grid dd {
  margin: 4px 0 0;
  color: #181816;
  font-size: 14px;
  font-weight: 700;
}

.actions {
  margin-top: 16px;
}

.muted,
.summary {
  color: #686861;
}

.summary {
  font-size: 14px;
}

@media (max-width: 760px) {
  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .debate-list,
  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
