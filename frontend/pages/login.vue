<script setup lang="ts">
const auth = useAuthStore()

const account = ref('')
const password = ref('')
const loading = ref(false)
const errorMessage = ref('')

async function submit() {
  errorMessage.value = ''
  loading.value = true

  try {
    await auth.login({
      account: account.value,
      password: password.value
    })
    await navigateTo('/')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <p class="eyebrow">DebateAI</p>
      <h1>登录</h1>

      <form class="form-stack" @submit.prevent="submit">
        <div>
          <label class="form-label" for="account">用户名或邮箱</label>
          <input id="account" v-model="account" class="field" autocomplete="username">
        </div>

        <div>
          <label class="form-label" for="password">密码</label>
          <input id="password" v-model="password" class="field" type="password" autocomplete="current-password">
        </div>

        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

        <button class="button button-primary" :disabled="loading" type="submit">
          {{ loading ? '登录中' : '登录' }}
        </button>
      </form>

      <p class="switch-line">
        还没有账号？
        <NuxtLink to="/register">注册</NuxtLink>
      </p>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}

.auth-card {
  width: min(100%, 420px);
  border: 1px solid #deded8;
  border-radius: 8px;
  padding: 28px;
  background: #fff;
}

.eyebrow {
  margin: 0 0 6px;
  color: #66665f;
  font-size: 14px;
  font-weight: 600;
}

h1 {
  margin: 0 0 24px;
  color: #181816;
  font-size: 30px;
}

.field {
  margin-top: 8px;
}

.button {
  width: 100%;
}

.switch-line {
  margin: 18px 0 0;
  color: #66665f;
  font-size: 14px;
  text-align: center;
}

.switch-line a {
  color: #111;
  font-weight: 700;
}
</style>
