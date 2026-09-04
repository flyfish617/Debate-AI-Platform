<script setup lang="ts">
import ReportButton from '~/components/shared/ReportButton.vue'

type Message = {
  id: number
  role: 'user' | 'ai' | 'system'
  content: string
  round: number
  aiProvider: string | null
  aiModel: string | null
  latencyMs: number | null
  status: string
  createdAt: string
}

type DebateDetail = {
  id: number
  topic: {
    id: number
    title: string
    description: string | null
    category: string
  }
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
  viewerVote: 'user' | 'ai' | null
  messages: Message[]
  createdAt: string
  endedAt: string | null
}

type DebateComment = {
  id: number
  debateId: number
  userId: number
  username: string
  content: string
  status: string
  createdAt: string
}

type CommentPage = {
  list: DebateComment[]
  total: number
  page: number
  size: number
}

const route = useRoute()
const auth = useAuthStore()
const { request, apiUrl } = useApi()
const stream = useStream()

const debate = ref<DebateDetail | null>(null)
const comments = ref<DebateComment[]>([])
const commentTotal = ref(0)
const input = ref('')
const commentInput = ref('')
const streamReply = ref('')
const streamStatus = ref<'idle' | 'streaming' | 'done' | 'error'>('idle')
const retryUserMessageId = ref<number | null>(null)
const loading = ref(false)
const ending = ref(false)
const loadingComments = ref(false)
const submittingComment = ref(false)
const voting = ref(false)
const errorMessage = ref('')
const commentError = ref('')
const voteError = ref('')
const messageListRef = ref<HTMLElement | null>(null)
const hasLoadedDebate = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const stanceLabel = computed(() => {
  if (!debate.value) return ''
  return debate.value.userStance === 'pro' ? '正方' : '反方'
})

const aiStanceLabel = computed(() => {
  if (!debate.value) return ''
  return debate.value.aiStance === 'pro' ? '正方' : '反方'
})

const statusLabel = computed(() => {
  if (!debate.value) return ''
  if (debate.value.status === 'active') return '进行中'
  if (debate.value.status === 'ending') return '结辩生成中'
  if (debate.value.status === 'ended') return '已结束'
  if (debate.value.status === 'failed') return '结辩失败'
  return debate.value.status
})

const winnerLabel = computed(() => {
  if (!debate.value?.winner) return '待定'
  return debate.value.winner === 'user' ? '用户胜' : debate.value.winner === 'ai' ? 'AI 胜' : '平局'
})

const isOwner = computed(() => Boolean(auth.user && debate.value && auth.user.id === debate.value.userId))
const canSend = computed(() => isOwner.value && debate.value?.status === 'active' && streamStatus.value !== 'streaming')
const canRetryAiReply = computed(() => canSend.value && retryUserMessageId.value !== null)
const participantLabel = computed(() => isOwner.value ? '你的立场' : '用户立场')
const roomModeLabel = computed(() => isOwner.value ? '创建者模式' : '围观模式')
const canVote = computed(() => Boolean(debate.value && debate.value.status === 'ended' && !isOwner.value))
const canViewComments = computed(() => Boolean(debate.value && (!isOwner.value || debate.value.status === 'ended')))
const canSubmitComment = computed(() => canViewComments.value && !isOwner.value)

onMounted(async () => {
  await loadDebate()
  await loadComments()
  refreshTimer = setInterval(() => {
    if (streamStatus.value !== 'streaming') {
      loadDebate(true).then(() => loadComments(true))
    }
  }, 3000)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
  stream.cancel()
})

async function loadDebate(silent = false) {
  const shouldStickToBottom = !hasLoadedDebate.value || isMessageListNearBottom()
  if (!silent) {
    loading.value = true
  }
  errorMessage.value = ''

  try {
    debate.value = await request<DebateDetail>(`/debate/${route.params.id}`)
    hasLoadedDebate.value = true
    if (shouldStickToBottom) {
      scrollMessagesToBottom()
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

function isMessageListNearBottom() {
  const element = messageListRef.value
  if (!element) return true
  const distance = element.scrollHeight - element.scrollTop - element.clientHeight
  return distance < 80
}

function scrollMessagesToBottom(smooth = false) {
  nextTick(() => {
    const element = messageListRef.value
    if (!element) return
    element.scrollTo({
      top: element.scrollHeight,
      behavior: smooth ? 'smooth' : 'auto'
    })
  })
}

async function loadComments(silent = false) {
  if (!canViewComments.value) {
    comments.value = []
    commentTotal.value = 0
    return
  }

  if (!silent) {
    loadingComments.value = true
  }
  commentError.value = ''

  try {
    const data = await request<CommentPage>(`/debate/${route.params.id}/comment?page=1&size=20`)
    comments.value = data.list
    commentTotal.value = data.total
  } catch (error) {
    commentError.value = error instanceof Error ? error.message : String(error)
  } finally {
    if (!silent) {
      loadingComments.value = false
    }
  }
}

async function send() {
  if (!input.value.trim() || !canSend.value) return

  streamReply.value = ''
  errorMessage.value = ''
  retryUserMessageId.value = null
  streamStatus.value = 'streaming'
  scrollMessagesToBottom()

  try {
    await stream.start(apiUrl(`/debate/${route.params.id}/message`), { content: input.value }, {
      onAccepted(payload) {
        const data = payload as { user_message_id?: number }
        retryUserMessageId.value = data.user_message_id || null
      },
      onToken(text) {
        const shouldStickToBottom = isMessageListNearBottom()
        streamReply.value += text
        if (shouldStickToBottom) {
          scrollMessagesToBottom(true)
        }
      },
      onDone() {
        streamStatus.value = 'done'
        input.value = ''
        retryUserMessageId.value = null
        loadDebate().then(() => {
          streamReply.value = ''
        })
      },
      onError(payload) {
        streamStatus.value = 'error'
        const data = payload as { message?: string, user_message_id?: number }
        retryUserMessageId.value = data.user_message_id || retryUserMessageId.value
        errorMessage.value = data.message || 'AI 回复生成失败，请稍后重试'
        loadDebate()
      }
    })
  } catch (error) {
    streamStatus.value = 'error'
    errorMessage.value = error instanceof Error ? error.message : String(error)
  }
}

async function retryAiReply() {
  if (!canRetryAiReply.value || retryUserMessageId.value === null) return

  streamReply.value = ''
  errorMessage.value = ''
  streamStatus.value = 'streaming'
  scrollMessagesToBottom()

  try {
    await stream.start(apiUrl(`/debate/${route.params.id}/message/retry`), { userMessageId: retryUserMessageId.value }, {
      onToken(text) {
        const shouldStickToBottom = isMessageListNearBottom()
        streamReply.value += text
        if (shouldStickToBottom) {
          scrollMessagesToBottom(true)
        }
      },
      onDone() {
        streamStatus.value = 'done'
        retryUserMessageId.value = null
        loadDebate().then(() => {
          streamReply.value = ''
        })
      },
      onError(payload) {
        streamStatus.value = 'error'
        const data = payload as { message?: string, user_message_id?: number }
        retryUserMessageId.value = data.user_message_id || retryUserMessageId.value
        errorMessage.value = data.message || 'AI 回复生成失败，请稍后重试'
        loadDebate()
      }
    })
  } catch (error) {
    streamStatus.value = 'error'
    errorMessage.value = error instanceof Error ? error.message : String(error)
  }
}

async function submitComment() {
  if (!commentInput.value.trim() || submittingComment.value) return
  if (!canSubmitComment.value) return
  if (!auth.user) {
    await navigateTo('/login')
    return
  }

  submittingComment.value = true
  commentError.value = ''

  try {
    await request<DebateComment>(`/debate/${route.params.id}/comment`, {
      method: 'POST',
      body: JSON.stringify({ content: commentInput.value })
    })
    commentInput.value = ''
    await loadComments()
  } catch (error) {
    commentError.value = error instanceof Error ? error.message : String(error)
  } finally {
    submittingComment.value = false
  }
}

async function vote(votedFor: 'user' | 'ai') {
  if (!debate.value || voting.value) return
  if (!auth.user) {
    await navigateTo('/login')
    return
  }

  voting.value = true
  voteError.value = ''

  try {
    const result = await request<{
      debateId: number
      votedFor: 'user' | 'ai'
      userVoteCount: number
      aiVoteCount: number
      winner: 'user' | 'ai' | 'draw'
    }>('/vote', {
      method: 'POST',
      body: JSON.stringify({
        debateId: debate.value.id,
        votedFor
      })
    })
    debate.value.userVoteCount = result.userVoteCount
    debate.value.aiVoteCount = result.aiVoteCount
    debate.value.winner = result.winner
    debate.value.viewerVote = result.votedFor
  } catch (error) {
    voteError.value = error instanceof Error ? error.message : String(error)
  } finally {
    voting.value = false
  }
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

function cancel() {
  stream.cancel()
  streamStatus.value = 'idle'
}

async function endDebate() {
  if (!debate.value || !isOwner.value || debate.value.status !== 'active' || ending.value) return

  ending.value = true
  errorMessage.value = ''

  try {
    await request(`/debate/${route.params.id}/end`, { method: 'POST' })
    await loadDebate()
    await loadComments()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    ending.value = false
  }
}
</script>

<template>
  <main class="page">
    <section class="shell">
      <header class="topbar">
        <div>
          <p class="eyebrow">辩论房间 #{{ route.params.id }}</p>
          <h1>{{ debate?.topic.title || '加载中' }}</h1>
        </div>
        <nav class="nav-actions">
          <span v-if="debate" class="mode-badge">{{ roomModeLabel }}</span>
          <ReportButton
            v-if="debate"
            target-type="debate"
            :target-id="debate.id"
            label="举报辩论"
          />
          <NuxtLink v-if="debate" class="button button-secondary" :to="`/topic/${debate.topic.id}`">查看话题</NuxtLink>
        </nav>
      </header>

      <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

      <section v-if="debate" class="meta-grid">
        <div class="meta-item">
          <span>{{ participantLabel }}</span>
          <strong>{{ stanceLabel }}</strong>
        </div>
        <div class="meta-item">
          <span>AI 立场</span>
          <strong>{{ aiStanceLabel }}</strong>
        </div>
        <div class="meta-item">
          <span>风格</span>
          <strong>{{ debate.style === 'mild' ? '温和' : '激烈' }}</strong>
        </div>
        <div class="meta-item">
          <span>可见性</span>
          <strong>{{ debate.visibility === 'public' ? '公开' : '私密' }}</strong>
        </div>
        <div class="meta-item">
          <span>状态</span>
          <strong>{{ statusLabel }}</strong>
        </div>
        <div class="meta-item">
          <span>轮次</span>
          <strong>{{ debate.currentRound }} / {{ debate.maxRounds }}</strong>
        </div>
        <div class="meta-item">
          <span>结果</span>
          <strong>{{ winnerLabel }}</strong>
        </div>
      </section>

      <div v-if="debate" class="workspace">
        <section class="panel debate-panel">
          <div class="panel-head">
            <h2>消息记录</h2>
            <span>{{ statusLabel }}</span>
          </div>

          <div ref="messageListRef" class="message-list">
            <article
              v-for="message in debate.messages"
              :key="message.id"
              class="message"
              :class="[message.role, { pending: message.status === 'pending', failed: message.status === 'failed' }]"
            >
              <div class="message-head">
                <strong>{{ message.role === 'ai' ? 'AI 辩手' : '用户' }}</strong>
                <span>{{ message.status === 'pending' ? '生成中' : message.status === 'failed' ? '生成失败' : `第 ${message.round} 轮` }}</span>
              </div>
              <p>{{ message.content }}</p>
            </article>

            <article v-if="streamReply" class="message ai">
              <div class="message-head">
                <strong>AI 辩手</strong>
                <span>{{ streamStatus }}</span>
              </div>
              <p>{{ streamReply }}</p>
            </article>
          </div>
        </section>

        <aside class="side-column">
          <section v-if="debate.status === 'ended'" class="panel vote-panel">
            <div class="panel-head">
              <h2>投票裁决</h2>
              <span>{{ winnerLabel }}</span>
            </div>

            <p v-if="voteError" class="form-error">{{ voteError }}</p>

            <div class="vote-grid">
              <button
                class="vote-option"
                :class="{ active: debate.viewerVote === 'user' }"
                type="button"
                :disabled="!canVote || voting"
                @click="vote('user')"
              >
                <span>用户方</span>
                <strong>{{ debate.userVoteCount }}</strong>
              </button>
              <button
                class="vote-option"
                :class="{ active: debate.viewerVote === 'ai' }"
                type="button"
                :disabled="!canVote || voting"
                @click="vote('ai')"
              >
                <span>AI 方</span>
                <strong>{{ debate.aiVoteCount }}</strong>
              </button>
            </div>

            <p v-if="isOwner" class="end-note">创建者不能参与自己辩论的投票。</p>
            <p v-else-if="!auth.user" class="end-note">登录后可以参与投票。</p>
            <p v-else-if="debate.viewerVote" class="end-note">你已投给{{ debate.viewerVote === 'user' ? '用户方' : 'AI 方' }}，可以改票。</p>
            <p v-else class="end-note">辩论已结束，请投出你的判断。</p>
          </section>

          <section v-else-if="isOwner" class="panel composer-panel">
            <label for="message">你的发言</label>
            <textarea
              id="message"
              v-model="input"
              :disabled="debate.status !== 'active'"
              :placeholder="debate.status === 'active' ? '输入你的下一轮观点' : '这场辩论已经结束'"
            />
            <div class="actions">
              <button class="button button-primary" :disabled="!canSend" @click="send">
                {{ streamStatus === 'streaming' ? '生成中' : '发送' }}
              </button>
              <button
                v-if="canRetryAiReply"
                class="button button-secondary"
                type="button"
                @click="retryAiReply"
              >
                重试 AI 回复
              </button>
              <button class="button button-secondary" type="button" :disabled="streamStatus !== 'streaming'" @click="cancel">取消</button>
              <button
                v-if="isOwner && debate.status === 'active'"
                class="button button-danger"
                type="button"
                :disabled="ending || streamStatus === 'streaming'"
                @click="endDebate"
              >
                {{ ending ? '结辩中' : '结束辩论' }}
              </button>
            </div>
            <p v-if="debate.status === 'ended'" class="end-note">
              辩论已结束，结果：{{ winnerLabel }}。
            </p>
            <p v-else-if="debate.status === 'ending'" class="end-note">
              辩论已进入结辩阶段，AI 结辩完成后会自动刷新展示。
            </p>
            <p v-else-if="debate.status === 'active'" class="end-note">
              达到 {{ debate.maxRounds }} 轮后会自动进入结辩阶段。
            </p>
            <p v-else-if="debate.status === 'failed'" class="end-note">
              AI 结辩生成失败，请稍后刷新或重新发起辩论。
            </p>
          </section>

          <section v-if="canViewComments" class="panel comment-panel">
            <div class="panel-head">
              <h2>场外评论</h2>
              <span>{{ commentTotal }} 条</span>
            </div>

            <p v-if="commentError" class="form-error">{{ commentError }}</p>

            <form v-if="canSubmitComment" class="comment-form" @submit.prevent="submitComment">
              <textarea
                v-model="commentInput"
                class="comment-textarea"
                :disabled="submittingComment"
                :placeholder="auth.user ? '写下你的场外观点' : '登录后参与场外评论'"
              />
              <button class="button button-primary" type="submit" :disabled="submittingComment || !commentInput.trim()">
                {{ submittingComment ? '发布中' : '发布评论' }}
              </button>
            </form>
            <p v-else class="end-note">辩论已经结束，现在可以查看观众的场外评论。</p>

            <div v-if="loadingComments" class="comment-empty">
              <strong>加载评论中...</strong>
            </div>
            <div v-else-if="comments.length === 0" class="comment-empty">
              <strong>暂无场外评论</strong>
              <p>消息会自动刷新，第一条场外观点可以从这里开始。</p>
            </div>
            <div v-else class="comment-list">
              <article v-for="comment in comments" :key="comment.id" class="comment-item">
                <div class="comment-head">
                  <NuxtLink class="comment-author" :to="`/profile/${comment.username}`">{{ comment.username }}</NuxtLink>
                  <div class="comment-tools">
                    <span>{{ formatTime(comment.createdAt) }}</span>
                    <ReportButton target-type="comment" :target-id="comment.id" label="举报评论" />
                  </div>
                </div>
                <p>{{ comment.content }}</p>
              </article>
            </div>
          </section>
        </aside>
      </div>

      <article v-else-if="loading" class="panel muted">加载辩论中...</article>
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
.panel-head,
.actions,
.message-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.topbar {
  justify-content: space-between;
  padding-bottom: 20px;
  border-bottom: 1px solid #deded8;
}

.nav-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.mode-badge {
  border: 1px solid #d6d6cf;
  border-radius: 999px;
  padding: 7px 11px;
  color: #4b4b46;
  background: #fff;
  font-size: 13px;
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

.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
  margin-top: 24px;
}

.meta-item,
.panel {
  border: 1px solid #deded8;
  border-radius: 8px;
  background: #fff;
}

.meta-item {
  display: grid;
  gap: 6px;
  padding: 14px;
}

.meta-item span,
.panel-head span,
.message-head span {
  color: #686861;
  font-size: 13px;
}

.meta-item strong {
  color: #181816;
  font-size: 17px;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  align-items: start;
  gap: 20px;
  margin-top: 20px;
}

.panel {
  padding: 20px;
}

.debate-panel {
  display: flex;
  flex-direction: column;
  height: min(68vh, 720px);
  min-height: 520px;
}

.message-list {
  flex: 1;
  min-height: 0;
  margin-top: 14px;
  overflow-y: auto;
  padding-right: 8px;
  overscroll-behavior: contain;
}

.message-list::-webkit-scrollbar {
  width: 8px;
}

.message-list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #c8c8c0;
}

.side-column {
  display: grid;
  gap: 16px;
  position: sticky;
  top: 92px;
}

.panel-head {
  justify-content: space-between;
}

.message {
  margin-top: 16px;
  border-radius: 8px;
  padding: 14px;
  background: #f7f7f5;
}

.message.ai {
  background: #f1f1ed;
}

.message.pending {
  border: 1px dashed #d6d6cf;
  background: #fbfbf8;
}

.message.user {
  background: #eef4ff;
}

.message.failed {
  border: 1px solid #fecaca;
  background: #fff1f2;
}

.message-head {
  justify-content: space-between;
}

.message p {
  margin: 10px 0 0;
  color: #2f2f2b;
  line-height: 1.8;
  white-space: pre-wrap;
}

label {
  display: block;
  color: #474741;
  font-size: 14px;
  font-weight: 650;
}

textarea {
  width: 100%;
  min-height: 180px;
  margin-top: 12px;
  resize: vertical;
  border: 1px solid #c8c8c0;
  border-radius: 7px;
  padding: 12px;
  color: #262622;
  outline: none;
  line-height: 1.6;
}

textarea:focus {
  border-color: #33332f;
}

textarea:disabled {
  color: #686861;
  background: #f7f7f5;
  cursor: not-allowed;
}

.actions {
  flex-wrap: wrap;
  margin-top: 16px;
}

.button-danger {
  color: #fff;
  background: #9f1239;
  border-color: #9f1239;
}

.vote-panel {
  min-height: 220px;
}

.vote-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.vote-option {
  display: grid;
  gap: 8px;
  border: 1px solid #d6d6cf;
  border-radius: 8px;
  padding: 16px;
  color: #2f2f2b;
  background: #fff;
  cursor: pointer;
  text-align: left;
}

.vote-option strong {
  color: #181816;
  font-size: 28px;
}

.vote-option.active {
  border-color: #1f1f1c;
  background: #f1f1ed;
}

.vote-option:disabled {
  cursor: not-allowed;
  opacity: 0.76;
}

.end-note {
  margin: 14px 0 0;
  color: #686861;
  font-size: 14px;
}

.comment-panel {
  min-height: 260px;
}

.comment-form {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.comment-textarea {
  min-height: 104px;
  margin-top: 0;
}

.comment-empty {
  display: grid;
  place-items: center;
  min-height: 190px;
  margin-top: 16px;
  border: 1px dashed #d6d6cf;
  border-radius: 8px;
  padding: 24px;
  color: #686861;
  text-align: center;
}

.comment-empty strong {
  color: #181816;
}

.comment-empty p {
  margin: 8px 0 0;
  line-height: 1.7;
}

.comment-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.comment-item {
  border-top: 1px solid #eeeeea;
  padding-top: 12px;
}

.comment-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #686861;
  font-size: 13px;
}

.comment-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.comment-author {
  color: #181816;
  font-weight: 700;
  text-decoration: none;
}

.comment-item p {
  margin: 8px 0 0;
  color: #2f2f2b;
  line-height: 1.7;
  white-space: pre-wrap;
}

.muted {
  color: #686861;
}

@media (max-width: 860px) {
  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .meta-grid,
  .workspace {
    grid-template-columns: 1fr;
  }

  .debate-panel {
    height: 60vh;
    min-height: 420px;
  }

  .side-column {
    position: static;
  }
}
</style>
