import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createRouter, createWebHistory } from 'vue-router'
import { setActivePinia, createPinia } from 'pinia'

const routes = [
  { path: '/login', name: 'Login', component: { template: '<div>Login</div>' }, meta: { guest: true } },
  { path: '/register', name: 'Register', component: { template: '<div>Register</div>' }, meta: { guest: true } },
  {
    path: '/', component: { template: '<div><router-view/></div>' }, meta: { requiresAuth: true },
    children: [
      { path: 'upload', name: 'Upload', component: { template: '<div>Upload</div>' } },
      { path: 'report/:taskId', name: 'Report', component: { template: '<div>Report</div>' } },
      { path: 'history', name: 'History', component: { template: '<div>History</div>' } },
    ]
  },
]

describe('router navigation guard', () => {
  let router

  beforeEach(async () => {
    setActivePinia(createPinia())
    localStorage.clear()

    router = createRouter({
      history: createWebHistory(),
      routes,
    })
    const { useAuthStore } = await import('@/stores/auth')
    router.beforeEach((to, _from, next) => {
      const auth = useAuthStore()
      if (to.meta.requiresAuth && !auth.isAuthenticated) {
        next('/login')
      } else if (to.meta.guest && auth.isAuthenticated) {
        next('/')
      } else {
        next()
      }
    })
  })

  it('redirects to /login when accessing protected route without auth', async () => {
    await router.push('/upload')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('redirects to /login when accessing /history without auth', async () => {
    await router.push('/history')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('allows access to /login without auth', async () => {
    await router.push('/login')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('allows access to /register without auth', async () => {
    await router.push('/register')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/register')
  })

  it('redirects authenticated user away from guest routes', async () => {
    localStorage.setItem('token', 'valid-token')
    const { useAuthStore } = await import('@/stores/auth')
    const store = useAuthStore()
    store.token = 'valid-token'

    await router.push('/login')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('allows authenticated user to access protected routes', async () => {
    localStorage.setItem('token', 'valid-token')
    const { useAuthStore } = await import('@/stores/auth')
    const store = useAuthStore()
    store.token = 'valid-token'

    await router.push('/upload')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/upload')
  })

  it('allows authenticated user to access /history', async () => {
    localStorage.setItem('token', 'valid-token')
    const { useAuthStore } = await import('@/stores/auth')
    const store = useAuthStore()
    store.token = 'valid-token'

    await router.push('/history')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/history')
  })
})
