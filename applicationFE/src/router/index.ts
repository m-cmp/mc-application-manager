import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      redirect: '/web/softwareCatalog'
    },
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
    {
      path: '/web/generate/yaml',
      name: 'yamlGenerate',
      component: () => import('@/views/generate/YamlGenerate.vue' as any)
    },
    {
      path: '/web/repository/list',
      name: 'repositoryList',
      component: () => import('@/views/repository/RepositoryList.vue' as any)
    },
    {
      path: `/web/repository/detail/:repositoryName`,
      name: 'repositoryDetail',
      component: () => import('@/views/repository/RepositoryDetail.vue' as any)
    },
    {
      path: `/web/softwareCatalog`,
      name: 'softwareCatalog',
      component: () => import('@/views/softwareCatalog/SoftwareCatalog.vue' as any)
    },
    // {
    //   path: `/web/softwareCatalog/list`,
    //   name: 'softwareCatalogList',
    //   component: () => import('@/views/softwareCatalog/SoftwareCatalogList.vue' as any)
    // },
    {
      path: `/web/softwareCatalog/list/test`,
      name: 'softwareCatalogListTest',
      component: () => import('@/views/softwareCatalog/SoftwareCatalogListTest.vue' as any)
    },
  ]
})

export default router
