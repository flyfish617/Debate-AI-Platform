import { defineStore } from 'pinia'

export type UserProfile = {
  id: number
  username: string
  email: string
  avatarUrl: string | null
  bio: string | null
  role: string
  status: string
  points: number
  wins: number
  losses: number
}

type AuthResponse = {
  token: string
  expiresIn: number
  user: UserProfile
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>('')
  const user = ref<UserProfile | null>(null)
  const initialized = ref(false)

  const isAuthenticated = computed(() => Boolean(token.value && user.value))

  function restore() {
    if (initialized.value || !import.meta.client) return
    token.value = localStorage.getItem('debate_ai_token') || ''
    const rawUser = localStorage.getItem('debate_ai_user')
    user.value = rawUser ? JSON.parse(rawUser) : null
    initialized.value = true
  }

  function persist(auth: AuthResponse) {
    token.value = auth.token
    user.value = auth.user
    if (import.meta.client) {
      localStorage.setItem('debate_ai_token', auth.token)
      localStorage.setItem('debate_ai_user', JSON.stringify(auth.user))
    }
  }

  function updateToken(nextToken: string) {
    if (!nextToken) return
    token.value = nextToken
    if (import.meta.client) {
      localStorage.setItem('debate_ai_token', nextToken)
    }
  }

  function clear() {
    token.value = ''
    user.value = null
    if (import.meta.client) {
      localStorage.removeItem('debate_ai_token')
      localStorage.removeItem('debate_ai_user')
    }
  }

  async function register(payload: { username: string; email: string; password: string }) {
    const { request } = useApi()
    const data = await request<AuthResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload)
    })
    persist(data)
  }

  async function login(payload: { account: string; password: string }) {
    const { request } = useApi()
    const data = await request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload)
    })
    persist(data)
  }

  async function refreshMe() {
    if (!token.value) return
    const { request } = useApi()
    user.value = await request<UserProfile>('/auth/me')
    if (import.meta.client) {
      localStorage.setItem('debate_ai_user', JSON.stringify(user.value))
    }
  }

  async function updateProfile(payload: { avatarUrl?: string | null; bio?: string | null }) {
    const { request } = useApi()
    user.value = await request<UserProfile>('/auth/me', {
      method: 'PATCH',
      body: JSON.stringify(payload)
    })
    if (import.meta.client) {
      localStorage.setItem('debate_ai_user', JSON.stringify(user.value))
    }
  }

  async function logout() {
    const { request } = useApi()
    try {
      if (token.value) {
        await request<string>('/auth/logout', { method: 'POST' })
      }
    } finally {
      clear()
      await navigateTo('/login')
    }
  }

  return {
    token,
    user,
    initialized,
    isAuthenticated,
    restore,
    register,
    login,
    updateToken,
    refreshMe,
    updateProfile,
    logout,
    clear
  }
})
