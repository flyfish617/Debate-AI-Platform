<script setup lang="ts">
const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const editForm = reactive({
  avatarUrl: '',
  bio: ''
})

const avatarInitial = computed(() => auth.user?.username?.slice(0, 1).toUpperCase() || '我')

function syncEditForm() {
  editForm.avatarUrl = auth.user?.avatarUrl || ''
  editForm.bio = auth.user?.bio || ''
}

function beginEdit() {
  syncEditForm()
  editing.value = true
  errorMessage.value = ''
  successMessage.value = ''
}

function cancelEdit() {
  syncEditForm()
  editing.value = false
  errorMessage.value = ''
}

async function saveProfile() {
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    await auth.updateProfile({
      avatarUrl: editForm.avatarUrl,
      bio: editForm.bio
    })
    syncEditForm()
    editing.value = false
    successMessage.value = '个人资料已保存'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  if (!auth.user) {
    await navigateTo('/login')
    return
  }

  loading.value = true
  try {
    await auth.refreshMe()
    syncEditForm()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main class="page">
    <section class="shell">
      <header class="topbar">
        <div>
          <p class="eyebrow">个人中心</p>
          <h1>{{ auth.user?.username || '我的账号' }}</h1>
        </div>
        <nav v-if="auth.user" class="nav-actions">
          <button v-if="!editing" class="button button-primary" type="button" @click="beginEdit">编辑资料</button>
          <NuxtLink v-if="auth.user.role === 'admin'" class="button button-primary" to="/admin">管理后台</NuxtLink>
          <NuxtLink class="button button-secondary" :to="`/profile/${auth.user.username}`">公开主页</NuxtLink>
          <button class="button button-secondary" type="button" @click="auth.logout">登出</button>
        </nav>
      </header>

      <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
      <p v-if="successMessage" class="form-success">{{ successMessage }}</p>

      <article v-if="auth.user" class="panel">
        <div class="profile-head">
          <div class="avatar">
            <img v-if="auth.user.avatarUrl" :src="auth.user.avatarUrl" alt="用户头像">
            <span v-else>{{ avatarInitial }}</span>
          </div>
          <div>
            <h2>{{ auth.user.username }}</h2>
            <p>{{ auth.user.bio || '这个用户还没有填写简介。' }}</p>
          </div>
        </div>

        <dl class="profile-grid">
          <div>
            <dt>用户名</dt>
            <dd>{{ auth.user.username }}</dd>
          </div>
          <div>
            <dt>邮箱</dt>
            <dd>{{ auth.user.email }}</dd>
          </div>
          <div>
            <dt>角色</dt>
            <dd>{{ auth.user.role }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd>{{ auth.user.status }}</dd>
          </div>
          <div>
            <dt>积分</dt>
            <dd>{{ auth.user.points }}</dd>
          </div>
          <div>
            <dt>战绩</dt>
            <dd>{{ auth.user.wins }} 胜 / {{ auth.user.losses }} 负</dd>
          </div>
        </dl>

        <form v-if="editing" class="edit-form" @submit.prevent="saveProfile">
          <label>
            <span>头像 URL</span>
            <input v-model.trim="editForm.avatarUrl" type="url" maxlength="500" placeholder="https://example.com/avatar.png">
          </label>
          <label>
            <span>个人简介</span>
            <textarea v-model.trim="editForm.bio" maxlength="200" rows="4" placeholder="介绍一下你的辩论风格或关注领域"></textarea>
          </label>
          <div class="form-footer">
            <span>{{ editForm.bio.length }}/200</span>
            <div class="form-actions">
              <button class="button button-secondary" type="button" :disabled="saving" @click="cancelEdit">取消</button>
              <button class="button button-primary" type="submit" :disabled="saving">
                {{ saving ? '保存中...' : '保存资料' }}
              </button>
            </div>
          </div>
        </form>
      </article>

      <article v-else-if="loading" class="panel muted">加载个人信息中...</article>
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

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid #deded8;
}

.nav-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
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

h2 {
  margin: 0 0 8px;
  color: #181816;
  font-size: 24px;
}

.panel {
  margin-top: 28px;
  border: 1px solid #deded8;
  border-radius: 8px;
  padding: 22px;
  background: #fff;
}

.profile-head {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 18px;
  align-items: center;
  padding-bottom: 22px;
  border-bottom: 1px solid #eeeeea;
}

.profile-head p {
  margin: 0;
  color: #55554f;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.avatar {
  width: 84px;
  height: 84px;
  border: 1px solid #d7d7d0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: #f4f4ef;
  color: #3d3d38;
  font-size: 30px;
  font-weight: 800;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin: 22px 0 0;
}

.profile-grid div {
  border-bottom: 1px solid #eeeeea;
  padding-bottom: 12px;
}

.profile-grid dt {
  color: #686861;
  font-size: 13px;
}

.profile-grid dd {
  margin: 6px 0 0;
  color: #181816;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.edit-form {
  display: grid;
  gap: 16px;
  margin-top: 24px;
  padding-top: 22px;
  border-top: 1px solid #eeeeea;
}

.edit-form label {
  display: grid;
  gap: 8px;
  color: #3f3f39;
  font-weight: 700;
}

.edit-form input,
.edit-form textarea {
  width: 100%;
  border: 1px solid #cfcfc8;
  border-radius: 8px;
  padding: 11px 12px;
  color: #181816;
  font: inherit;
  line-height: 1.5;
  background: #fff;
}

.edit-form textarea {
  resize: vertical;
}

.form-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #686861;
  font-size: 13px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.form-success {
  margin: 18px 0 0;
  color: #207245;
  font-weight: 700;
}

.muted {
  color: #686861;
}

@media (max-width: 760px) {
  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-head {
    grid-template-columns: 1fr;
  }

  .profile-grid {
    grid-template-columns: 1fr;
  }

  .form-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
