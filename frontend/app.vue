<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { request } = useApi()
const unreadNotificationCount = ref(0)

const navItems = computed(() => [
  { label: '首页', to: '/', active: route.path === '/' },
  { label: '话题广场', to: '/topic', active: route.path.startsWith('/topic') && route.path !== '/topic/new' },
  { label: '辩论大厅', to: '/debate', active: route.path.startsWith('/debate') },
  { label: '排行榜', to: '/leaderboard', active: route.path.startsWith('/leaderboard') },
  { label: '创建话题', to: '/topic/new', active: route.path === '/topic/new' },
  ...(auth.user ? [{ label: unreadNotificationCount.value > 0 ? `通知(${unreadNotificationCount.value})` : '通知', to: '/notification', active: route.path.startsWith('/notification') }] : []),
  ...(auth.user?.role === 'admin' ? [{ label: '管理后台', to: '/admin', active: route.path.startsWith('/admin') }] : []),
  { label: auth.user ? auth.user.username : '我的', to: auth.user ? '/me' : '/login', active: route.path === '/me' || route.path === '/login' || route.path === '/register' }
])

const showAppHeader = computed(() => route.path !== '/')
const canGoBack = computed(() => route.path !== '/')
const primaryNavItems = computed(() => navItems.value.filter(item => item.to !== '/topic/new' && item.to !== '/admin'))

onMounted(loadUnreadNotificationCount)

watch(() => auth.user?.id, () => loadUnreadNotificationCount())
watch(() => route.path, () => loadUnreadNotificationCount())

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    navigateTo('/')
  }
}

async function loadUnreadNotificationCount() {
  if (!auth.user) {
    unreadNotificationCount.value = 0
    return
  }
  try {
    const data = await request<{ count: number }>('/notification/unread-count')
    unreadNotificationCount.value = data.count
  } catch {
    unreadNotificationCount.value = 0
  }
}
</script>

<template>
  <div>
    <header v-if="showAppHeader" class="app-header">
      <div class="app-header-inner">
        <div class="brand-row">
          <button v-if="canGoBack" class="back-button" type="button" @click="goBack">返回</button>
          <NuxtLink class="brand-link" to="/">DebateAI</NuxtLink>
        </div>

        <nav class="main-nav" aria-label="主导航">
          <NuxtLink
            v-for="item in navItems"
            :key="item.label"
            class="nav-link"
            :class="{ active: item.active }"
            :to="item.to"
          >
            {{ item.label }}
          </NuxtLink>
        </nav>
      </div>
    </header>

    <NuxtPage />

    <nav v-if="showAppHeader" class="mobile-bottom-nav" aria-label="移动端主导航">
      <NuxtLink
        v-for="item in primaryNavItems"
        :key="`mobile-${item.label}`"
        class="mobile-nav-link"
        :class="{ active: item.active }"
        :to="item.to"
      >
        <span>{{ item.label }}</span>
      </NuxtLink>
    </nav>
  </div>
</template>
