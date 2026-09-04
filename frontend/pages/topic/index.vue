<script setup lang="ts">
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

type TopicPage = {
  list: Topic[]
  total: number
  page: number
  size: number
}

const categories = ['全部', '科技', '社会', '哲学', '教育', '娱乐', '其他']
const activeCategory = ref('全部')
const topics = ref<Topic[]>([])
const total = ref(0)
const loading = ref(false)
const deletingTopicId = ref<number | null>(null)
const errorMessage = ref('')
const successMessage = ref('')

const auth = useAuthStore()
const { request } = useApi()

onMounted(loadTopics)

async function loadTopics() {
  loading.value = true
  errorMessage.value = ''

  try {
    const query = activeCategory.value === '全部'
      ? '?page=1&size=20'
      : `?page=1&size=20&category=${encodeURIComponent(activeCategory.value)}`
    const data = await request<TopicPage>(`/topic${query}`)
    topics.value = data.list
    total.value = data.total
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
}

async function selectCategory(category: string) {
  activeCategory.value = category
  await loadTopics()
}

async function deleteTopic(topic: Topic) {
  if (auth.user?.role !== 'admin') return
  const confirmed = window.confirm(`确定删除话题“${topic.title}”吗？删除后话题广场和关联辩论大厅都不会再展示。`)
  if (!confirmed) return

  deletingTopicId.value = topic.id
  errorMessage.value = ''
  successMessage.value = ''

  try {
    await request(`/admin/topics/${topic.id}/delete`, { method: 'POST' })
    successMessage.value = '已删除话题'
    await loadTopics()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    deletingTopicId.value = null
  }
}
</script>

<template>
  <main class="page">
    <section class="shell">
      <header class="topbar">
        <div>
          <h1>话题广场</h1>
        </div>
        <nav class="nav-actions">
          <button class="button button-secondary" :disabled="loading" @click="loadTopics">刷新</button>
        </nav>
      </header>

      <div class="filters">
        <button
          v-for="category in categories"
          :key="category"
          class="chip"
          :class="{ active: activeCategory === category }"
          @click="selectCategory(category)"
        >
          {{ category }}
        </button>
      </div>

      <p v-if="errorMessage" class="form-error">
        {{ errorMessage }}
      </p>
      <p v-if="successMessage" class="form-success">{{ successMessage }}</p>

      <section class="topic-list">
        <article v-if="loading" class="topic-card muted">加载中...</article>
        <article v-else-if="topics.length === 0" class="topic-card muted">
          当前分类还没有话题。可以切换分类，或登录后创建一个。
        </article>

        <article v-for="topic in topics" :key="topic.id" class="topic-card">
          <div class="topic-card-head">
            <span class="badge">{{ topic.category }}</span>
            <span>{{ topic.debateCount }} 场辩论</span>
          </div>
          <h2>{{ topic.title }}</h2>
          <p>{{ topic.description || '暂无描述' }}</p>
          <footer>
            <span>浏览 {{ topic.viewCount }}</span>
            <span>#{{ topic.id }}</span>
          </footer>
          <div class="topic-actions">
            <NuxtLink class="button button-secondary" :to="`/topic/${topic.id}`">查看话题</NuxtLink>
            <NuxtLink
              v-if="auth.user"
              class="button button-primary"
              :to="`/debate/new?topicId=${topic.id}`"
            >
              开始辩论
            </NuxtLink>
            <NuxtLink v-else class="button button-primary" to="/login">登录后辩论</NuxtLink>
            <button
              v-if="auth.user?.role === 'admin'"
              class="button button-danger"
              type="button"
              :disabled="deletingTopicId === topic.id"
              @click="deleteTopic(topic)"
            >
              {{ deletingTopicId === topic.id ? '删除中' : '删除话题' }}
            </button>
          </div>
        </article>
      </section>

      <p class="summary">共 {{ total }} 个话题</p>
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

.nav-actions,
.filters,
.topic-actions {
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

.topic-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 24px;
}

.topic-card {
  border: 1px solid #deded8;
  border-radius: 8px;
  padding: 18px;
  background: #fff;
}

.topic-card-head,
.topic-card footer {
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

.topic-card h2 {
  margin-top: 14px;
  font-size: 19px;
}

.topic-card p {
  min-height: 52px;
  margin: 10px 0 18px;
  color: #4b4b46;
  line-height: 1.7;
}

.topic-actions {
  margin-top: 16px;
}

.muted {
  color: #686861;
}

.summary {
  color: #686861;
  font-size: 14px;
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
  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .topic-list {
    grid-template-columns: 1fr;
  }
}
</style>
