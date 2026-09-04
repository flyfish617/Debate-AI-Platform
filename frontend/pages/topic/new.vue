<script setup lang="ts">
type Topic = {
  id: number
}

const categories = ['科技', '社会', '哲学', '教育', '娱乐', '其他']

const auth = useAuthStore()
const { request } = useApi()

const title = ref('')
const description = ref('')
const category = ref('科技')
const loading = ref(false)
const errorMessage = ref('')

onMounted(() => {
  if (!auth.user) {
    navigateTo('/login')
  }
})

async function submit() {
  errorMessage.value = ''
  loading.value = true

  try {
    const topic = await request<Topic>('/topic', {
      method: 'POST',
      body: JSON.stringify({
        title: title.value,
        description: description.value,
        category: category.value
      })
    })
    await navigateTo(`/topic/${topic.id}`)
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
          <p class="eyebrow">DebateAI</p>
          <h1>创建话题</h1>
        </div>
        <NuxtLink class="button button-secondary" to="/topic">返回话题广场</NuxtLink>
      </header>

      <form class="panel form-stack" @submit.prevent="submit">
        <div>
          <label class="form-label" for="title">标题</label>
          <input id="title" v-model="title" class="field" maxlength="100">
        </div>

        <div>
          <label class="form-label" for="category">分类</label>
          <select id="category" v-model="category" class="field">
            <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
          </select>
        </div>

        <div>
          <label class="form-label" for="description">描述</label>
          <textarea id="description" v-model="description" class="field textarea" maxlength="2000" />
        </div>

        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

        <button class="button button-primary" :disabled="loading" type="submit">
          {{ loading ? '创建中' : '创建话题' }}
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

.shell {
  width: min(100%, 760px);
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

h1 {
  margin: 0;
  color: #181816;
  font-size: 32px;
  line-height: 1.2;
}

.panel {
  margin-top: 28px;
  border: 1px solid #deded8;
  border-radius: 8px;
  padding: 22px;
  background: #fff;
}

.field {
  margin-top: 8px;
}

.textarea {
  min-height: 150px;
  resize: vertical;
  line-height: 1.7;
}

select.field {
  background: #fff;
}

@media (max-width: 760px) {
  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
