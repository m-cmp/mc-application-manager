import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/web',
      name: 'rootOssList',
      component: () => import('@/views/oss/OssList.vue' as any)
    },
    {
      path: '/web/oss/list',
      name: 'ossList',
      component: () => import('@/views/oss/OssList.vue' as any)
    },
  ]
})

export default router
