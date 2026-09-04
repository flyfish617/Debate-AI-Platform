import { defineStore } from 'pinia'

export const useDebateStore = defineStore('debate', () => {
  const messages = ref<{ role: 'user' | 'ai'; content: string }[]>([])
  const streamStatus = ref<'idle' | 'streaming' | 'done' | 'error'>('idle')

  function reset() {
    messages.value = []
    streamStatus.value = 'idle'
  }

  return { messages, streamStatus, reset }
})
