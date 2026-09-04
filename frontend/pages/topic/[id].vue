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

const route = useRoute()
const auth = useAuthStore()
const { request } = useApi()

const topic = ref<Topic | null>(null)
const loading = ref(false)
const errorMessage = ref('')

onMounted(loadTopic)

async function loadTopic() {
  loading.value = true
  errorMessage.value = ''

  try {
    topic.value = await request<Topic>(`/topic/${route.params.id}`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="page">
    <section class="shell">
      <header class="topbar">
        <div>
          <p class="eyebrow">话题详情</p>
          <h1>{{ topic?.title || '加载中' }}</h1>
        </div>
        <ReportButton
          v-if="topic"
          target-type="topic"
          :target-id="topic.id"
          label="举报话题"
        />
      </header>

      <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

      <article v-if="topic" class="panel">
        <div class="meta">
          <span class="badge">{{ topic.category }}</span>
          <span>#{{ topic.id }}</span>
          <span>{{ topic.debateCount }} 场辩论</span>
          <span>浏览 {{ topic.viewCount }}</span>
        </div>
        <p class="description">{{ topic.description || '暂无描述' }}</p>
        <div class="actions">
          <NuxtLink
            v-if="auth.user"
            class="button button-primary"
            :to="`/debate/new?topicId=${topic.id}`"
          >
            用这个话题发起辩论
          </NuxtLink>
          <NuxtLink v-else class="button button-primary" to="/login">登录后发起辩论</NuxtLink>
        </div>
      </article>

      <article v-else-if="loading" class="panel muted">加载话题中...</article>
    </section>
  </main>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

.shell {
  width: min(100%, 900px);
  min-height: 100vh;
  margin: 0 auto;
  padding: 32px 24px;
}

.topbar,
.nav-actions,
.meta,
.actions {
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

.eyebrow {
  margin: 0 0 6px;
  color: #66665f;
  font-size: 14px;
  font-weight: 600;
}

h1 {
  margin: 0;
  color: #181816;
  font-size: 32px;
  line-height: 1.25;
}

.panel {
  margin-top: 28px;
  border: 1px solid #deded8;
  border-radius: 8px;
  padding: 22px;
  background: #fff;
}

.meta {
  color: #686861;
  font-size: 14px;
}

.badge {
  border-radius: 999px;
  padding: 3px 8px;
  color: #4b4b46;
  background: #f1f1ed;
}

.description {
  margin: 20px 0 0;
  color: #2f2f2b;
  line-height: 1.8;
  white-space: pre-wrap;
}

.actions {
  margin-top: 24px;
}

.muted {
  color: #686861;
}

@media (max-width: 760px) {
  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
