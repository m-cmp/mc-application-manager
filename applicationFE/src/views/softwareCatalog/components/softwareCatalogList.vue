<template>
  <div ref="sofwareCatalog">
    <!-- Navbar -->
    <div class="row">
      <div class="col-lg-12">
        <div class="card">
          <div class="list-group card-list-group" id="sc-list-group">
            <div 
              class="list-group-item pe-1" 
              v-for="(catalog, idx) in catalogList" 
              :key="idx">
              <div class="row g-2 align-items-center">
                <!-- <div class="col-auto fs-3">{{ idx + 1 }}</div> -->
                <div class="col-auto me-3">
                  <img
                    v-if="catalog.resolvedLogoUrl && !catalog.logoLoadFailed"
                    :src="catalog.resolvedLogoUrl"
                    class="rounded catalog-icon"
                    alt="Catalog Icon"
                    width="40"
                    height="40"
                    @error="onCatalogIconError(catalog)">
                  <div
                    v-else
                    class="rounded catalog-icon-fallback d-flex align-items-center justify-content-center">
                    <IconPackage class="icon" size="22" stroke-width="1.75" />
                  </div>
                </div>
                
                <!-- Catalog Name -->
                <div class="col-5" @click="showSoftwareCatalogDetail(idx)">
                  {{ catalog.name }}
                  
                  <!-- Catalog Summary -->
                  <div class="text-muted">
                    {{ catalog.summary }}
                  </div>
                </div>

                <div class="col-3 d-flex justify-content-end"  @click="showSoftwareCatalogDetail(idx)">
                  <span class="text-muted" style="width: auto; text-align: right;">
                    <IconStarFilled class="icon me-1" width="12" height="12" stroke-width="1" color="#e5b942" />
                    <span style="color: #e5b942">
                      {{ catalog.averageRating || 0 }}
                    </span>
                    <span style="color: #e5b942;">
                      ({{ catalog.ratingCount || 0 }})
                    </span>
                  </span>
                  <span class="text-muted" style="width: 80px; text-align: right;">
                    <IconCloudDownload class="icon me-1" width="12" height="12" stroke-width="1" color="gray" />
                    <span style="color: gray;">
                      {{ catalog.downloadCount || 0 }} 
                    </span>
                  </span>
                </div>
                
                <!-- Catalog Category -->
                <div class="col-3 text-muted">
                  <div class="d-flex justify-content-end">
                    <div class="mouse-hover">
                      <IconEdit class="me-2 cursor-pointer" size="15" stroke-width="2" data-bs-toggle="modal" data-bs-target="#modal-wizard" @click="onClickUpdate(catalog.id)" />
                      <IconTrash class="cursor-pointer" size="15" stroke-width="2" @click="onClickDelete(catalog)" />
                    </div>
                  </div>
                  <div class="d-flex justify-content-end"  @click="showSoftwareCatalogDetail(idx)">
                    <span class="text-muted">
                      {{ catalog.category.length > 25 ? catalog.category.substring(0, 25) + "..." : catalog.category}}
                    </span>
                  </div>
                </div>

                <!-- Dots -->
                <!-- <div class="col-auto lh-1">
                  <div class="dropdown">
                    <a href="javascript:void(0);" class="link-secondary" @click="toggleDropdown(`dropdown-${catalog.id}`)">
                      <IconDots class="icon" width="24" height="24" stroke-width="2" />
                    </a>
                    <div :id="`dropdown-${catalog.id}`" class="dropdown-menu dropdown-menu-end" :class="{ 'show': activeDropdown === `dropdown-${catalog.id}` }">
                      <a 
                        class="dropdown-item" 
                        @click="onClickUpdate(catalog.id)" 
                        data-bs-toggle="modal"
                        data-bs-target="#modal-wizard">
                        Update
                      </a>
                      <a 
                        class="dropdown-item" 
                        @click="onClickDelete(catalog)"
                        href="javascript:void(0);">
                        Delete
                      </a>
                    </div>
                  </div>
                </div> -->

                <div 
                  :id="'accordion_' + catalog.id" 
                  class="accordion-collapse collapse"
                  :style="[catalog.isShow ? {display: 'block'} : {display:'none'}]">
                  <div class="accordion-body pt-0">
                    <div 
                      class="mt-3 mb-5" 
                      v-html="formattedText(catalog.description)" />
                      <div>

                        <!-- Ref Information(Hompage) -->
                        <strong>Ref Information</strong>
                        <ul :id="`${idx}-entity-ul`">
                          <template v-if="hasProperty(catalog.refData, 'HOMEPAGE')">
                            <template v-for="(homepage, idx) in catalog.refData.HOMEPAGE" :key="idx">
                              <li>
                                <a 
                                  class="btn"
                                  @click="goToPage(homepage.refValue)" >
                                  {{ homepage.refValue }}
                                </a>
                              </li>  
                            </template>
                          </template>
                        </ul>

                        <!-- Tags -->
                        <strong>TAGS</strong>
                        <ul :id="`${idx}-tag-ul`">
                          <template v-if="hasProperty(catalog.refData, 'TAG')">
                            <template v-for="(tag, idx) in catalog.refData.TAG" :key="idx">
                              <span>#{{ tag.refValue }} &nbsp;</span>
                            </template>
                          </template>
                        </ul>

                        <!-- Recommended Spec -->
                        <strong>Recommended Spec</strong>
                        <ul :id="`${idx}-tag-ul`">
                          <template
                            v-if="catalog.recommendedCpu && catalog.recommendedMemory && catalog.recommendedDisk">
                            <button class="btn btn-sm" style="margin-right: 5px;">
                              CPU : {{ catalog.recommendedCpu }} Core
                            </button>
                            <button class="btn btn-sm" style="margin-right: 5px;">
                              MEMORY : {{ catalog.recommendedMemory }} GB
                            </button>
                            <button class="btn btn-sm" style="margin-right: 5px;">
                              DISK : {{ catalog.recommendedDisk }} GB
                            </button>
                          </template>
                        </ul>

                        <div class="mt-4">
                          <div class="d-flex justify-content-between align-items-center mb-2">
                            <strong>Deployment Status</strong>
                            <button
                              type="button"
                              class="btn btn-sm btn-icon btn-ghost-secondary"
                              title="Refresh deployment status"
                              aria-label="Refresh deployment status"
                              :disabled="catalog.deploymentStatusLoading"
                              @click.stop="loadDeploymentStatus(catalog)">
                              <IconRefresh class="icon" size="18" stroke-width="1.75" />
                            </button>
                          </div>

                          <div v-if="catalog.deploymentStatusLoading" class="text-center text-muted py-3">
                            Loading deployment status...
                          </div>

                          <div v-else class="table-responsive">
                            <table class="table table-sm table-vcenter">
                              <thead>
                                <tr>
                                  <th>Type</th>
                                  <th>Target</th>
                                  <th>CSP</th>
                                  <th>Status</th>
                                  <th>IP/Endpoint</th>
                                  <th>Last Checked</th>
                                  <th class="text-end">Detail</th>
                                </tr>
                              </thead>
                              <tbody>
                                <tr v-if="catalog.deploymentStatuses.length === 0">
                                  <td colspan="7" class="text-center text-muted">
                                    No deployment status available
                                  </td>
                                </tr>
                                <tr
                                  v-for="deployment in catalog.deploymentStatuses"
                                  :key="deployment.rowKey">
                                  <td>{{ deployment.deploymentType }}</td>
                                  <td>{{ deployment.target }}</td>
                                  <td>{{ deployment.csp }}</td>
                                  <td>
                                    <span :class="getApplicationStatusBadgeClass(deployment.status)">
                                      {{ getApplicationStatusLabel(deployment.status) }}
                                    </span>
                                  </td>
                                  <td>{{ deployment.ipOrEndpoint }}</td>
                                  <td>{{ deployment.lastCheckedOrDeployedAt }}</td>
                                  <td class="text-end">
                                    <button
                                      type="button"
                                      class="btn btn-outline-primary"
                                      :disabled="!deployment.deploymentId"
                                      @click.stop="openApplicationDetail(deployment.deploymentId)">
                                      Detail
                                    </button>
                                  </td>
                                </tr>
                              </tbody>
                            </table>
                          </div>
                        </div>
                        <!-- <br />
                        <div class="btn-list" style="width:70%;" v-for="wf in catalog.refData.workflow"
                          :key="wf.catalogRefIdx">
                          <a class="btn"
                            :class="{'btn-outline-primary': containsText('install', wf.referenceValue), 'btn-outline-danger' : containsText('uninstall', wf.referenceValue)}"
                            style="margin-bottom:10px;" @click="onClickDeploy(wf.referenceValue)"
                            data-bs-toggle='modal' data-bs-target='#install-form'>
                            {{ btnName(wf.referenceValue) }}
                          </a>
                          {{ wf.referenceValue }}
                          <button class="btn btn-primary" style="text-align: center !important; margin-bottom:10px;"
                            @click="onClickLog(wf.referenceValue)" id='log-btn' data-bs-toggle='modal'
                            data-bs-target='#softwareCatalogLog'>
                            &nbsp;LOG&nbsp;
                          </button>
                        </div> -->
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
  
  <DeleteConfirmModal 
    ref="deleteConfirmModal"
    :target-catalog="deleteTargetCatalog"
    @deleted="onCatalogDeleted"
    @close="onDeleteModalClose" />
    
  <SoftwareCatalogWizard 
    ref="wizardModal"
    :mode="wizardMode"
    @created="_getSoftwareCatalogList"
    @updated="_getSoftwareCatalogList" />
    
  <ApplicationDetailModal
    ref="applicationDetailModalRef"
    :deployment-id="selectedDeploymentId" />
    
  <!-- <SoftwareCatalogForm 
    :mode="formMode" 
    :catalog-idx="selectCatalogIdx" 
    :repository-application-info="repositoryApplicationInfo"
    :repository-name="repositoryName"
    @get-list="_getSoftwareCatalogList" /> -->
</template>
<script setup lang="ts">
// Component
import { IconEdit, IconTrash, IconStarFilled, IconCloudDownload, IconPackage, IconRefresh } from '@tabler/icons-vue'
import SoftwareCatalogWizard from './softwareCatalogWizard.vue';
// @ts-ignore
import DeleteConfirmModal from './DeleteConfirmModal.vue';
// @ts-ignore
import ApplicationDetailModal from './applicationDetailModal.vue';

// API
import { getSoftwareCatalogList, getCatalogDeploymentStatus } from '../../../api/softwareCatalog';
import {
  getApplicationStatusBadgeClass,
  getApplicationStatusLabel
} from '../applicationStatusDisplay'

// ETC
import { onMounted, ref } from 'vue';
import type { SoftwareCatalog } from '../../type/type';
import { useToast } from 'vue-toastification';
// @ts-ignore
import _ from 'lodash';

const toast = useToast()

const catalogList = ref([] as Array<SoftwareCatalog | any>)
const selectCatalogIdx = ref(null as number | null)
const selectCatalogId = ref(null as number | null)
const selectCatalogInfo = ref({} as any)

// const formMode = ref('new')
const wizardMode = ref('new')
const repositoryApplicationInfo = ref({} as any)
const repositoryName = ref("" as string)

// 삭제 관련 상태
const deleteTargetCatalog = ref({} as any)
const deleteConfirmModal = ref<any>(null)

// 위저드 모달 관련 상태
const wizardModal = ref<any>(null)

const selectedDeploymentId = ref(0 as number)
const applicationDetailModalRef = ref<any>(null)

/**
 * @Title Life Cycle
 * @Desc catalogList set Method call
 */
onMounted(async () => {
  _getSoftwareCatalogList()
})

const startRegistration = () => {
  wizardMode.value = 'new'
  selectCatalogId.value = null
  selectCatalogInfo.value = {}
  selectCatalogIdx.value = 0;
  repositoryApplicationInfo.value = {}
  repositoryName.value = ""
  
  // 모달이 열린 후 초기화
  setTimeout(() => {
    if (wizardModal.value && typeof wizardModal.value.initForCreate === 'function') {
      wizardModal.value.initForCreate()
    }
  }, 100)
}

defineExpose({
  startRegistration
})

/**
* @Method _getSoftwareCatalogList
* @Desc software catalog List get Method Call / set Data
*/
const _getSoftwareCatalogList = async () => {
  try {
    await getSoftwareCatalogList("").then(({ data }) => {
      _.forEach(data, function(item: any) {
        item.refData = groupedData(item.catalogRefs)
        item.isShow = false;
        item.deploymentStatuses = []
        item.deploymentStatusLoaded = false
        item.deploymentStatusLoading = false
        item.resolvedLogoUrl = resolveCatalogIconUrl(item)
        item.logoLoadFailed = false
      })
      catalogList.value = data;
    })
  } catch(error) {
    console.log(error)
    toast.error('Unable to retrieve data.')
  }
}

const groupedData = (catalogRefs: any) => {
  return catalogRefs.reduce((acc:any, item:any) => {
    if (!acc[item.refType]) {
      acc[item.refType] = [];
    }
    acc[item.refType].push(item);
    return acc;
  }, {});
}

/**
* @Method onClickUpdate
* @Desc Update SoftwareCatalog Popup set
*/
const onClickUpdate = (catalogId: number) => {
  // 선택된 catalog 찾기
  const selectedCatalog = catalogList.value.find(catalog => catalog.id === catalogId)
  
  selectCatalogId.value = catalogId
  selectCatalogInfo.value = selectedCatalog || {}
  wizardMode.value = 'update'
  
  // 모달이 열린 후 업데이트용 초기화
  setTimeout(() => {
    if (wizardModal.value && typeof wizardModal.value.initForUpdate === 'function') {
      wizardModal.value.initForUpdate(catalogId, selectedCatalog)
    }
  }, 100)
}

const onClickDelete = (catalog: any) => {
  deleteTargetCatalog.value = catalog
  
  // 모달 컴포넌트 열기
  if (deleteConfirmModal.value) {
    deleteConfirmModal.value.show()
  }
}

// 삭제 완료 이벤트 핸들러
const onCatalogDeleted = async () => {
  // 목록 새로고침
  await _getSoftwareCatalogList()
}

// 모달 닫기 이벤트 핸들러
const onDeleteModalClose = () => {
  deleteTargetCatalog.value = {}
}

const showSoftwareCatalogDetail = async (idx:any) => {
  const catalog = catalogList.value[idx]
  catalog.isShow = !catalog.isShow
  if (catalog.isShow && !catalog.deploymentStatusLoaded) {
    await loadDeploymentStatus(catalog)
  }
}

const loadDeploymentStatus = async (catalog: any) => {
  if (!catalog?.id) return

  catalog.deploymentStatusLoading = true
  try {
    const { data } = await getCatalogDeploymentStatus(catalog.id)
    catalog.deploymentStatuses = buildDeploymentStatusRows(data)
    catalog.deploymentStatusLoaded = true
  } catch (error) {
    console.log(error)
    catalog.deploymentStatuses = []
    toast.error('Unable to retrieve deployment status.')
  } finally {
    catalog.deploymentStatusLoading = false
  }
}

const buildDeploymentStatusRows = (data: any) => {
  const histories = Array.isArray(data?.deploymentHistories) ? data.deploymentHistories : []
  const statuses = Array.isArray(data?.applicationStatuses) ? data.applicationStatuses : []

  return statuses.map((status: any, index: number) => {
    const history = findMatchedHistory(status, histories)
    return toDeploymentStatusRow(history, status, `status-${status.id || index}`)
  })
}

const findMatchedHistory = (status: any, histories: any[]) => {
  if (!status) return null

  const byDeploymentHistoryId = histories.find((history: any) =>
    status.deploymentHistoryId &&
    String(status.deploymentHistoryId) === String(history.id)
  )
  if (byDeploymentHistoryId) return byDeploymentHistoryId

  return histories.find((history: any) =>
    sameValue(status.deploymentType, history.deploymentType) &&
    sameValue(status.namespace, history.namespace) &&
    (
      sameValue(status.vmId, history.vmId) ||
      sameValue(status.clusterName, history.clusterName)
    )
  )
}

const toDeploymentStatusRow = (history: any, status: any, rowKey: string) => {
  return {
    rowKey,
    deploymentId: status?.deploymentHistoryId || history?.id || null,
    deploymentType: displayValue(status?.deploymentType || history?.deploymentType),
    target: displayValue(getTarget(history, status)),
    csp: displayValue(history?.cloudProvider),
    status: displayValue(status?.status || status?.podStatus),
    ipOrEndpoint: displayValue(getEndpoint(history, status)),
    lastCheckedOrDeployedAt: displayValue(formatDateTime(status?.checkedAt))
  }
}

const getTarget = (history: any, status: any) => {
  const deploymentType = status?.deploymentType || history?.deploymentType
  const namespace = status?.namespace || history?.namespace
  const mciId = status?.mciId || history?.mciId
  const vmId = status?.vmId || history?.vmId
  const clusterName = status?.clusterName || history?.clusterName

  if (deploymentType === 'VM') {
    return [namespace, mciId, vmId].filter(Boolean).join(' / ')
  }
  if (deploymentType === 'K8S') {
    return [namespace, clusterName].filter(Boolean).join(' / ')
  }
  return [namespace, mciId, vmId, clusterName].filter(Boolean).join(' / ')
}

const getEndpoint = (history: any, status: any) => {
  const publicIp = status?.publicIp || history?.publicIp
  const servicePort = status?.servicePort || history?.servicePort
  const ingressHost = history?.ingressHost
  const ingressPath = history?.ingressPath

  if (history?.ingressEnabled && ['public-iks-k8s-nginx', 'private-iks-k8s-nginx'].includes(history?.ingressClass) && ingressHost) {
    return `${history?.ingressTlsEnabled ? 'https' : 'http'}://${ingressHost}${ingressPath || '/'}`
  }
  if (history?.releaseName?.startsWith('mcmp-jupyter-') && ingressHost) {
    return `http://${ingressHost}:30880/`
  }
  if (publicIp && servicePort) return `${publicIp}:${servicePort}`
  if (publicIp) return publicIp
  if (ingressHost && ingressPath) return `${ingressHost}${ingressPath}`
  if (ingressHost) return ingressHost
  return ''
}

const displayValue = (value: any) => {
  if (value === null || value === undefined || value === '') return '-'
  return value
}

const sameValue = (left: any, right: any) => {
  if (!left || !right) return false
  return String(left) === String(right)
}

const formatDateTime = (value: any) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}

const openApplicationDetail = (deploymentId: number | null) => {
  if (!deploymentId) return

  selectedDeploymentId.value = deploymentId
  const modal = document.getElementById('application-detail-modal')
  if (modal) {
    try {
      if ((window as any).bootstrap && (window as any).bootstrap.Modal) {
        const modalInstance = new (window as any).bootstrap.Modal(modal)
        modalInstance.show()
      } else {
        modal.style.display = 'block'
        modal.classList.add('show')
        document.body.classList.add('modal-open')
      }

      setTimeout(() => {
        if (applicationDetailModalRef.value) {
          applicationDetailModalRef.value.refreshData(selectedDeploymentId.value)
        }
      }, 100)
    } catch (error) {
      console.error('Error opening detail modal:', error)
    }
  }
}

const hasProperty = (data:any, prop:any) => {
  return Object.prototype.hasOwnProperty.call(data, prop);
}

const goToPage = (url:string) => {
  window.open(url)
}

const catalogIconUrlByName: Record<string, string> = {
  'apache tomcat': '/catalog-icons/apache-tomcat.png',
  'redis': '/catalog-icons/redis.svg',
  'nginx': '/catalog-icons/nginx.svg',
  'apache http server': '/catalog-icons/apache-http-server.svg',
  'nexus repository': '/catalog-icons/nexus-repository.svg',
  'mariadb': '/catalog-icons/mariadb.svg',
  'grafana': '/catalog-icons/grafana.svg',
  'prometheus': '/catalog-icons/prometheus.svg',
  'elasticsearch': '/catalog-icons/elasticsearch.svg'
}

const normalizeCatalogName = (name: any) => {
  return String(name || '').trim().toLowerCase()
}

const getInternalCatalogIconUrl = (catalog: any) => {
  return catalogIconUrlByName[normalizeCatalogName(catalog?.name)] || ''
}

const resolveCatalogIconUrl = (catalog: any) => {
  return getInternalCatalogIconUrl(catalog) || catalog?.logoUrlLarge || catalog?.logoUrlSmall || ''
}

const onCatalogIconError = (catalog: any) => {
  const internalIconUrl = getInternalCatalogIconUrl(catalog)
  if (internalIconUrl && catalog.resolvedLogoUrl !== internalIconUrl) {
    catalog.resolvedLogoUrl = internalIconUrl
    catalog.logoLoadFailed = false
    return
  }

  catalog.logoLoadFailed = true
}

const formattedText = (text:string) => {
  return text.replace(/\\n|\n/g, '<br/>');
}

</script>

<style>
@import url('https://rsms.me/inter/inter.css');
:root {
  --tblr-font-sans-serif: 'Inter Var', -apple-system, BlinkMacSystemFont, San Francisco, Segoe UI, Roboto, Helvetica Neue, sans-serif;
}
body {
  font-feature-settings: "cv03", "cv04", "cv11";
}

.btn-grid-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-column-gap: 10px;
  grid-row-gap: 10px;
}

/* .me-3 {
  margin-right: 3px;
} */
 .mouse-hover {
   opacity: 0;
   transition: opacity 0.15s ease-in-out;
 }
 .list-group-item:hover .mouse-hover {
   opacity: 1;
 }
 .catalog-icon,
 .catalog-icon-fallback {
   width: 40px;
   height: 40px;
   object-fit: contain;
 }
 .catalog-icon-fallback {
   color: #667085;
   background-color: #f1f5f9;
   border: 1px solid #dbe3ea;
 }
</style>
