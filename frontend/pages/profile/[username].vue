<script setup lang="ts">
import ReportButton from '~/components/shared/ReportButton.vue'

type Topic = {
  id: number
  title: string
  description: string | null
  category: string
  creatorId: number
  status: string
  debateCount: number
  viewCount: number
  createdAt: string
}

type DebateSummary = {
  id: number
  topic: Topic | null
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

type PublicUserProfile = {
  id: number
  username: string
  avatarUrl: string | null
  bio: string | null
  role: string
  status: string
  points: number
  wins: number
  losses: number
  createdAt: string
  createdTopicCount: number
  publicDebateCount: number
  endedPublicDebateCount: number
  commentCount: number
  recentTopics: Topic[]
  recentDebates: DebateSummary[]
}

const route = useRoute()
const auth = useAuthStore()
const { request } = useApi()

const profile = ref<PublicUserProfile | null>(null)
const loading = ref(false)
const errorMessage = ref('')

onMounted(loadProfile)

async function loadProfile() {
  loading.value = true
  errorMessage.value = ''

  try {
    profile.value = await request<PublicUserProfile>(`/user/${encodeURIComponent(String(route.params.username))}`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
}

function formatTime(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

function stanceLabel(stance: 'pro' | 'con') {
  return stance === 'pro' ? '正方' : '反方'
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
        <div class="profile-head">
          <div class="avatar">
            <img v-if="profile?.avatarUrl" :src="profile.avatarUrl" :alt="profile.username">
            <span v-else>{{ profile?.username?.slice(0, 1) || '?' }}</span>
          </div>
          <div>
            <p class="eyebrow">用户主页</p>
            <h1>{{ profile?.username || route.params.username }}</h1>
            <p v-if="profile?.bio" class="bio">{{ profile.bio }}</p>
            <p v-else-if="profile" class="bio">这个用户还没有填写个人简介。</p>
          </div>
        </div>
        <nav class="nav-actions">
          <ReportButton
            v-if="profile && auth.user?.id !== profile.id"
            target-type="user"
            :target-id="profile.id"
            label="举报用户"
          />
          <button class="button button-secondary" type="button" :disabled="loading" @click="loadProfile">刷新</button>
        </nav>
      </header>

      <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

      <article v-if="loading" class="panel muted">加载用户主页中...</article>

      <template v-else-if="profile">
        <section class="stats-grid">
          <article class="stat-item">
            <span>积分</span>
            <strong>{{ profile.points }}</strong>
          </article>
          <article class="stat-item">
            <span>战绩</span>
            <strong>{{ profile.wins }} / {{ profile.losses }}</strong>
          </article>
          <article class="stat-item">
            <span>话题</span>
            <strong>{{ profile.createdTopicCount }}</strong>
          </article>
          <article class="stat-item">
            <span>公开辩论</span>
            <strong>{{ profile.publicDebateCount }}</strong>
          </article>
          <article class="stat-item">
            <span>已结束</span>
            <strong>{{ profile.endedPublicDebateCount }}</strong>
          </article>
          <article class="stat-item">
            <span>评论</span>
            <strong>{{ profile.commentCount }}</strong>
          </article>
        </section>

        <section class="content-grid">
          <article class="panel">
            <div class="panel-head">
              <h2>近期公开辩论</h2>
              <span>{{ profile.recentDebates.length }} 场</span>
            </div>

            <div v-if="profile.recentDebates.length === 0" class="empty-state">暂无公开辩论。</div>
            <div v-else class="item-list">
              <NuxtLink
                v-for="debate in profile.recentDebates"
                :key="debate.id"
                class="list-item"
                :to="`/debate/${debate.id}`"
              >
                <div>
                  <strong>{{ debate.topic?.title || `辩论 #${debate.id}` }}</strong>
                  <p>{{ debate.topic?.description || '暂无话题描述' }}</p>
                </div>
                <dl>
                  <div>
                    <dt>立场</dt>
                    <dd>{{ stanceLabel(debate.userStance) }}</dd>
                  </div>
                  <div>
                    <dt>状态</dt>
                    <dd>{{ statusLabel(debate.status) }}</dd>
                  </div>
                  <div>
                    <dt>结果</dt>
                    <dd>{{ winnerLabel(debate.winner) }}</dd>
                  </div>
                </dl>
              </NuxtLink>
            </div>
          </article>

          <article class="panel">
            <div class="panel-head">
              <h2>近期话题</h2>
              <span>{{ profile.recentTopics.length }} 个</span>
            </div>

            <div v-if="profile.recentTopics.length === 0" class="empty-state">暂无公开话题。</div>
            <div v-else class="item-list">
              <NuxtLink
                v-for="topic in profile.recentTopics"
                :key="topic.id"
                class="topic-item"
                :to="`/topic/${topic.id}`"
              >
                <span>{{ topic.category }}</span>
                <strong>{{ topic.title }}</strong>
                <small>{{ formatTime(topic.createdAt) }}</small>
              </NuxtLink>
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
  width: min(100%, 1100px);
  min-height: 100vh;
  margin: 0 auto;
  padding: 32px 24px;
}

.topbar,
.nav-actions,
.profile-head,
.panel-head {
  display: flex;
  align-items: center;
  gap: 16px;
}

.topbar {
  justify-content: space-between;
  padding-bottom: 20px;
  border-bottom: 1px solid #deded8;
}

.nav-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.profile-head {
  min-width: 0;
}

.avatar {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  flex: 0 0 auto;
  overflow: hidden;
  border: 1px solid #d6d6cf;
  border-radius: 8px;
  color: #181816;
  background: #f1f1ed;
  font-size: 28px;
  font-weight: 800;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.eyebrow,
.bio {
  margin: 0;
  color: #66665f;
}

.eyebrow {
  margin-bottom: 6px;
  font-size: 14px;
  font-weight: 650;
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
  font-size: 18px;
}

.bio {
  margin-top: 8px;
  line-height: 1.6;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-top: 24px;
}

.stat-item,
.panel {
  border: 1px solid #deded8;
  border-radius: 8px;
  background: #fff;
}

.stat-item {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.stat-item span,
.panel-head span,
.list-item dt,
.topic-item span,
.topic-item small {
  color: #686861;
  font-size: 13px;
}

.stat-item strong {
  color: #181816;
  font-size: 22px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 18px;
  margin-top: 18px;
}

.panel {
  padding: 20px;
}

.panel-head {
  justify-content: space-between;
}

.item-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.list-item,
.topic-item {
  display: grid;
  gap: 10px;
  border-top: 1px solid #eeeeea;
  padding-top: 12px;
  color: inherit;
  text-decoration: none;
}

.list-item strong,
.topic-item strong {
  color: #181816;
}

.list-item p {
  margin: 6px 0 0;
  color: #4b4b46;
  line-height: 1.6;
}

.list-item dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.list-item dd {
  margin: 4px 0 0;
  color: #181816;
  font-weight: 700;
}

.topic-item {
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
}

.topic-item span {
  border-radius: 999px;
  padding: 3px 8px;
  background: #f1f1ed;
}

.empty-state,
.muted {
  color: #686861;
}

.empty-state {
  margin-top: 16px;
  border: 1px dashed #d6d6cf;
  border-radius: 8px;
  padding: 24px;
  text-align: center;
}

@media (max-width: 860px) {
  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .stats-grid,
  .content-grid,
  .list-item dl,
  .topic-item {
    grid-template-columns: 1fr;
  }
}
</style>
