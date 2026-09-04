export default defineNuxtConfig({
  compatibilityDate: '2026-06-03',
  modules: ['@pinia/nuxt'],
  css: ['~/assets/css/main.css'],
  runtimeConfig: {
    public: {
      apiBase: 'http://localhost:8080/api'
    }
  },
  devtools: {
    enabled: true
  }
})
