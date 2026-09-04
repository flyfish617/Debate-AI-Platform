<script setup lang="ts">
type Topic = {
  id: number
  title: string
  description: string | null
  category: string
}

type CreateDebateResponse = {
  debateId: number
  topicId: number
  maxRounds: number
}

const route = useRoute()
const auth = useAuthStore()
const { request } = useApi()

const topic = ref<Topic | null>(null)
const topicId = computed(() => Number(route.query.topicId || 0))
const userStance = ref<'pro' | 'con'>('pro')
const style = ref<'mild' | 'intense'>('mild')
const visibility = ref<'public' | 'private'>('public')
const maxRounds = ref(5)
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')

const stanceText = computed(() => userStance.value === 'pro' ? '正方' : '反方')
const aiStanceText = computed(() => userStance.value === 'pro' ? '反方' : '正方')
const styleText = computed(() => style.value === 'mild' ? '温和' : '激烈')
const visibilityText = computed(() => visibility.value === 'public' ? '公开' : '私密')
const normalizedMaxRounds = computed(() => Math.max(5, Number(maxRounds.value) || 5))

const createPayload = computed(() => ({
  topicId: topicId.value,
  userStance: userStance.value,
  style: style.value,
  visibility: visibility.value,
  maxRounds: normalizedMaxRounds.value
}))

onMounted(async () => {
  if (!auth.user) {
    await navigateTo('/login')
    return
  }
  await loadTopic()
})

async function loadTopic() {
  if (!topicId.value) {
    errorMessage.value = '缺少话题，请先从话题广场选择一个话题。'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    topic.value = await request<Topic>(`/topic/${topicId.value}`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
}

async function createDebate() {
  errorMessage.value = ''
  submitting.value = true

  try {
    const debate = await request<CreateDebateResponse>('/debate', {
      method: 'POST',
      body: JSON.stringify(createPayload.value)
    })
    await navigateTo(`/debate/${debate.debateId}`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="page">
    <section class="shell">
      <header class="topbar">
        <div>
          <p class="eyebrow">创建辩论</p>
          <h1>{{ topic?.title || '选择辩论配置' }}</h1>
        </div>
      </header>

      <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

      <div class="workspace">
        <form class="panel form-stack" @submit.prevent="createDebate">
          <div>
            <span class="form-label">你的立场</span>
            <div class="segmented">
              <button type="button" :class="{ active: userStance === 'pro' }" @click="userStance = 'pro'">正方</button>
              <button type="button" :class="{ active: userStance === 'con' }" @click="userStance = 'con'">反方</button>
            </div>
          </div>

          <div>
            <span class="form-label">辩论风格</span>
            <div class="segmented">
              <button type="button" :class="{ active: style === 'mild' }" @click="style = 'mild'">温和</button>
              <button type="button" :class="{ active: style === 'intense' }" @click="style = 'intense'">激烈</button>
            </div>
          </div>

          <div>
            <span class="form-label">可见性</span>
            <div class="segmented">
              <button type="button" :class="{ active: visibility === 'public' }" @click="visibility = 'public'">公开</button>
              <button type="button" :class="{ active: visibility === 'private' }" @click="visibility = 'private'">私密</button>
            </div>
          </div>

          <div>
            <label class="form-label" for="max-rounds">最大轮次</label>
            <input
              id="max-rounds"
              v-model.number="maxRounds"
              class="number-input"
              type="number"
              min="5"
              max="15"
              step="1"
              @blur="maxRounds = normalizedMaxRounds"
            >
          </div>

          <button class="button button-primary" :disabled="loading || submitting || !topic" type="submit">
            {{ submitting ? '创建中' : '创建并进入辩论' }}
          </button>
        </form>

        <aside class="panel">
          <h2>辩论设置</h2>
          <dl class="summary-list">
            <div>
              <dt>话题</dt>
              <dd>{{ topic?.title || '加载中' }}</dd>
            </div>
            <div>
              <dt>你的立场</dt>
              <dd>{{ stanceText }}</dd>
            </div>
            <div>
              <dt>AI 立场</dt>
              <dd>{{ aiStanceText }}</dd>
            </div>
            <div>
              <dt>风格</dt>
              <dd>{{ styleText }}</dd>
            </div>
            <div>
              <dt>可见性</dt>
              <dd>{{ visibilityText }}</dd>
            </div>
            <div>
              <dt>最大轮次</dt>
              <dd>{{ normalizedMaxRounds }} 轮</dd>
            </div>
          </dl>

          <div v-if="topic" class="topic-mini">
            <span class="badge">{{ topic.category }}</span>
            <p>{{ topic.description || '暂无描述' }}</p>
          </div>
        </aside>
      </div>
    </section>
  </main>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

.shell {
  width: min(100%, 1000px);
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

.eyebrow {
  margin: 0 0 6px;
  color: #66665f;
  font-size: 14px;
  font-weight: 600;
}

h1,
h2 {
  margin: 0;
  color: #181816;
}

h1 {
  font-size: 32px;
  line-height: 1.25;
}

h2 {
  font-size: 18px;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 20px;
  margin-top: 28px;
}

.panel {
  border: 1px solid #deded8;
  border-radius: 8px;
  padding: 22px;
  background: #fff;
}

.segmented {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-top: 9px;
}

.segmented button {
  border: 1px solid #c8c8c0;
  border-radius: 7px;
  padding: 10px 12px;
  color: #2f2f2b;
  background: #fff;
  cursor: pointer;
}

.segmented button.active {
  color: #fff;
  background: #1f1f1c;
  border-color: #1f1f1c;
}

.number-input {
  width: 100%;
  margin-top: 9px;
  border: 1px solid #c8c8c0;
  border-radius: 7px;
  padding: 10px 12px;
  color: #2f2f2b;
  background: #fff;
  outline: none;
}

.number-input:focus {
  border-color: #1f1f1c;
}

.summary-list {
  display: grid;
  gap: 12px;
  margin: 18px 0 0;
}

.summary-list div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #eeeeea;
  padding-bottom: 10px;
}

.summary-list dt {
  color: #686861;
  font-size: 14px;
}

.summary-list dd {
  margin: 0;
  color: #181816;
  font-weight: 700;
  text-align: right;
}

.topic-mini {
  display: grid;
  gap: 10px;
  margin-top: 18px;
  color: #4b4b46;
}

.topic-mini p {
  margin: 0;
  line-height: 1.7;
}

.badge {
  width: fit-content;
  border-radius: 999px;
  padding: 3px 8px;
  color: #4b4b46;
  background: #f1f1ed;
  font-size: 13px;
}

@media (max-width: 760px) {
  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .workspace {
    grid-template-columns: 1fr;
  }
}
</style>
