<script setup lang="ts">
const auth = useAuthStore()
</script>

<template>
  <main class="page">
    <section class="shell">
      <header class="topbar">
        <div>
          <p class="eyebrow">DebateAI</p>
          <h1>辩论房间</h1>
        </div>
        <nav class="nav-actions">
          <NuxtLink v-if="auth.user" class="badge" to="/me">{{ auth.user.username }}</NuxtLink>
          <button v-if="auth.user" class="button button-secondary" @click="auth.logout">登出</button>
          <NuxtLink v-if="!auth.user" class="button button-secondary" to="/login">登录</NuxtLink>
          <NuxtLink v-if="!auth.user" class="button button-primary" to="/register">注册</NuxtLink>
        </nav>
      </header>

      <section class="empty-state">
        <h2>从话题开始一场辩论</h2>
        <p>辩论房间现在会和具体话题绑定。先在话题广场选择一个话题，再配置立场、风格和可见性。</p>
        <div class="actions">
          <NuxtLink class="button button-primary" to="/topic">进入话题广场</NuxtLink>
          <NuxtLink class="button button-secondary" to="/debate">进入辩论大厅</NuxtLink>
          <NuxtLink class="button button-secondary" to="/leaderboard">查看排行榜</NuxtLink>
          <NuxtLink v-if="auth.user" class="button button-secondary" to="/topic/new">创建新话题</NuxtLink>
        </div>
      </section>
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
h2,
p {
  margin: 0;
}

h1 {
  color: #181816;
  font-size: 32px;
  line-height: 1.2;
}

.badge {
  border: 1px solid #d6d6cf;
  border-radius: 999px;
  padding: 7px 11px;
  color: #4b4b46;
  background: #fff;
  font-size: 13px;
  white-space: nowrap;
}

.empty-state {
  margin-top: 32px;
  border: 1px solid #deded8;
  border-radius: 8px;
  padding: 28px;
  background: #fff;
}

.empty-state h2 {
  color: #181816;
  font-size: 24px;
}

.empty-state p {
  max-width: 680px;
  margin-top: 12px;
  color: #4b4b46;
  line-height: 1.7;
}

.actions {
  margin-top: 22px;
}

@media (max-width: 760px) {
  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
