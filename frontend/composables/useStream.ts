type StreamHandlers = {
  onAccepted?: (payload: unknown) => void
  onToken?: (text: string) => void
  onDone?: (payload: unknown) => void
  onError?: (payload: unknown) => void
}

export function useStream() {
  const controller = shallowRef<AbortController | null>(null)

  async function start(url: string, body: unknown, handlers: StreamHandlers = {}) {
    controller.value?.abort()
    controller.value = new AbortController()
    const auth = useAuthStore()
    const headers: Record<string, string> = {
      'Content-Type': 'application/json'
    }

    if (auth.token) {
      headers.Authorization = `Bearer ${auth.token}`
    }

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal: controller.value.signal
    })

    if (!response.ok || !response.body) {
      const message = await readErrorMessage(response)
      throw new Error(message || `Stream request failed: ${response.status}`)
    }

    const refreshedToken = response.headers.get('X-New-Token')
    if (refreshedToken) {
      auth.updateToken(refreshedToken)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { value, done } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split('\n\n')
      buffer = frames.pop() || ''

      for (const frame of frames) {
        handleFrame(frame, handlers)
      }
    }
  }

  function cancel() {
    controller.value?.abort()
    controller.value = null
  }

  return { start, cancel }
}

function handleFrame(frame: string, handlers: StreamHandlers) {
  const event = frame.match(/^event:\s*(.+)$/m)?.[1]
  const dataText = frame.match(/^data:\s*(.+)$/m)?.[1]
  const data = dataText ? JSON.parse(dataText) : null

  if (event === 'token') handlers.onToken?.(data.text || '')
  if (event === 'accepted') handlers.onAccepted?.(data)
  if (event === 'done') handlers.onDone?.(data)
  if (event === 'error') handlers.onError?.(data)
}

async function readErrorMessage(response: Response) {
  const rawPayload = await response.text()
  if (!rawPayload) return ''

  try {
    const payload = JSON.parse(rawPayload)
    const details = payload.error?.details
    if (details && typeof details === 'object') {
      return Object.values(details).join('；')
    }
    return payload.error?.message || payload.message || ''
  } catch {
    return rawPayload
  }
}
