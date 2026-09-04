<script setup lang="ts">
type LeaderboardEntry = {
  rank: number
  userId: number
  username: string
  avatarUrl: string | null
  bio: string | null
  points: number
  wins: number
  losses: number
  winRate: number
  publicDebateCount: number
  endedPublicDebateCount: number
  createdAt: string
}

type LeaderboardPage = {
  list: LeaderboardEntry[]
  total: number
  page: number
  size: number
}

const sortOptions = [
  { label: '积分', value: 'points' },
  { label: '胜场', value: 'wins' },
  { label: '辩论数', value: 'debates' }
]

const { request } = useApi()
const entries = ref<LeaderboardEntry[]>([])
const total = ref(0)
const activeSort = ref('points')
const loading = ref(false)
const errorMessage = ref('')

onMounted(loadLeaderboard)

async function loadLeaderboard() {
  loading.value = true
  errorMessage.value = ''

  try {
    const data = await request<LeaderboardPage>(`/leaderboard?page=1&size=50&sort=${activeSort.value}`)
    entries.value = data.list
    total.value = data.total
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
}

async function selectSort(sort: string) {
  activeSort.value = sort
  await loadLeaderboard()
}

function rankClass(rank: number) {
  if (rank === 1) return 'gold'
  if (rank === 2) return 'silver'
  if (rank === 3) return 'bronze'
  return ''
}
</script>

<template>
  <main class="page">
    <section class="shell">
      <header class="topbar">
        <div>
          <p class="eyebrow">Leaderboard</p>
          <h1>排行榜</h1>
        </div>
        <nav class="nav-actions">
          <button class="button button-secondary" :disabled="loading" @click="loadLeaderboard">刷新</button>
        </nav>
      </header>

      <div class="filters">
        <button
          v-for="option in sortOptions"
          :key="option.value"
          class="chip"
          :class="{ active: activeSort === option.value }"
          @click="selectSort(option.value)"
        >
          {{ option.label }}
        </button>
      </div>

      <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

      <section class="leaderboard">
        <article v-if="loading" class="leader-row muted">加载排行榜中...</article>
        <article v-else-if="entries.length === 0" class="leader-row muted">
          暂时没有上榜用户。完成公开辩论后，积分和战绩会出现在这里。
        </article>

        <article v-for="entry in entries" :key="entry.userId" class="leader-row">
          <div class="rank" :class="rankClass(entry.rank)">{{ entry.rank }}</div>
          <div class="profile">
            <NuxtLink class="username" :to="`/profile/${entry.username}`">{{ entry.username }}</NuxtLink>
            <p>{{ entry.bio || '这个用户还没有填写简介。' }}</p>
          </div>
          <dl class="stats">
            <div>
              <dt>积分</dt>
              <dd>{{ entry.points }}</dd>
            </div>
            <div>
              <dt>战绩</dt>
              <dd>{{ entry.wins }} 胜 / {{ entry.losses }} 负</dd>
            </div>
            <div>
              <dt>胜率</dt>
              <dd>{{ entry.winRate.toFixed(1) }}%</dd>
            </div>
            <div>
              <dt>公开辩论</dt>
              <dd>{{ entry.publicDebateCount }}</dd>
            </div>
          </dl>
        </article>
      </section>

      <p class="summary">共 {{ total }} 位用户</p>
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

.topbar,
.nav-actions,
.filters,
.leader-row,
.stats {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.topbar {
  justify-content: space-between;
  padding-bottom: 20px;
  border-bottom: 1px solid #deded8;
}

.nav-actions {
  justify-content: flex-end;
}

.eyebrow {
  margin: 0 0 6px;
  color: #66665f;
  font-size: 14px;
  font-weight: 600;
}

h1,
p,
dl {
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

.leaderboard {
  display: grid;
  gap: 12px;
  margin-top: 24px;
}

.leader-row {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) minmax(360px, 0.95fr);
  gap: 16px;
  border: 1px solid #deded8;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

.rank {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 999px;
  color: #fff;
  background: #4b4b46;
  font-weight: 800;
}

.rank.gold {
  background: #9a6a00;
}

.rank.silver {
  background: #6b7280;
}

.rank.bronze {
  background: #9a5a24;
}

.profile {
  min-width: 0;
}

.username {
  color: #181816;
  font-size: 18px;
  font-weight: 800;
  text-decoration: none;
}

.profile p {
  margin-top: 6px;
  color: #686861;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-items: stretch;
}

.stats div {
  border-left: 1px solid #eeeeea;
  padding-left: 12px;
}

.stats dt {
  color: #686861;
  font-size: 13px;
}

.stats dd {
  margin: 6px 0 0;
  color: #181816;
  font-weight: 800;
}

.summary,
.muted {
  color: #686861;
}

.summary {
  margin-top: 18px;
}

@media (max-width: 860px) {
  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .leader-row {
    grid-template-columns: 48px minmax(0, 1fr);
  }

  .stats {
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
