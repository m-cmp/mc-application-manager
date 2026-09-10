import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      redirect: '/web/applications/swcatalog'
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
      redirect: (to) => ({
        name: 'repositoryList',
        query: to.query,
        hash: to.hash
      })
    },
    {
      path: `/web/repository/detail/:repositoryName`,
      redirect: (to) => ({
        name: 'repositoryDetail',
        params: { repositoryName: to.params.repositoryName },
        query: to.query,
        hash: to.hash
      })
    },
    {
      path: `/web/softwareCatalog/install`,
      name: 'softwareCatalogInstall',
      component: () => import('@/views/softwareCatalog/InstallSoftwareIframe.vue' as any)
    },
    {
      path: `/web/softwareCatalog`,
      redirect: (to) => ({
        name: 'softwareCatalog',
        query: to.query,
        hash: to.hash
      })
    },
    {
      path: `/web/applications/swcatalog`,
      name: 'softwareCatalog',
      component: () => import('@/views/softwareCatalog/SoftwareCatalog.vue' as any)
    },
    {
      path: `/web/applications/status`,
      name: 'applicationStatus',
      component: () => import('@/views/softwareCatalog/ApplicationStatus.vue' as any)
    },
    {
      path: `/web/applications/repository`,
      component: () => import('@/views/repository/ApplicationRepository.vue' as any),
      children: [
        {
          path: '',
          name: 'repositoryList',
          component: () => import('@/views/repository/RepositoryList.vue' as any)
        },
        {
          path: ':repositoryName',
          name: 'repositoryDetail',
          component: () => import('@/views/repository/RepositoryDetail.vue' as any)
        }
      ]
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
