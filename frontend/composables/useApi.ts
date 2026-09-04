export function useApi() {
  const config = useRuntimeConfig()

  function apiUrl(path: string) {
    return `${config.public.apiBase}${path.startsWith('/') ? path : `/${path}`}`
  }

  async function request<T>(path: string, options: RequestInit = {}) {
    const auth = useAuthStore()
    const headers = new Headers(options.headers)

    if (options.body && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }

    if (auth.token) {
      headers.set('Authorization', `Bearer ${auth.token}`)
    }

    let response: Response
    try {
      response = await fetch(apiUrl(path), {
        ...options,
        headers
      })
    } catch {
      throw new Error(`无法连接后端服务，请确认后端已启动，并检查 API 地址：${config.public.apiBase}`)
    }

    const refreshedToken = response.headers.get('X-New-Token')
    if (refreshedToken) {
      auth.updateToken(refreshedToken)
    }

    const rawPayload = await response.text()
    let payload: any = null
    try {
      payload = rawPayload ? JSON.parse(rawPayload) : null
    } catch {
      throw new Error(`后端返回格式异常：${response.status}`)
    }

    if (!response.ok || !payload?.success) {
      const details = payload.error?.details
      const detailText = details && typeof details === 'object'
        ? Object.values(details).join('；')
        : ''
      throw new Error(detailText || payload.error?.message || `Request failed: ${response.status}`)
    }

    return payload.data as T
  }

  return { apiUrl, request }
}
