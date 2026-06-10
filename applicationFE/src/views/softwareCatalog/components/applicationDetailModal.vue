<template>
  <div class="modal modal-blur fade" :id="props.modalId" tabindex="-1" role="dialog" aria-hidden="true">
    <div class="modal-dialog modal-xl modal-dialog-centered" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Application Detail</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close" @click="closeModal"></button>
        </div>
        <div class="modal-body" style="max-height: 80vh; overflow-y: auto;">
          <div v-if="loading" class="text-center">
            <div class="spinner-border" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
          </div>
          
          <div v-else-if="applicationDetail">
            <!-- Application Overview -->
            <div class="mb-4">
              <h6 class="text-primary">Application Overview</h6>
              <div class="row">
                <div class="col-md-2 text-center">
                  <img
                    v-if="applicationLogoUrl && !logoLoadFailed"
                    :src="applicationLogoUrl"
                    :alt="applicationDetail.catalogName"
                    class="img-fluid application-detail-logo"
                    @error="logoLoadFailed = true">
                  <div
                    v-else
                    class="application-detail-logo-fallback d-inline-flex align-items-center justify-content-center">
                    <IconPackage class="icon" size="32" stroke-width="1.75" />
                  </div>
                </div>
                <div class="col-md-10">
                  <h5>{{ applicationDetail.catalogName }}</h5>
                  <p class="text-muted">{{ applicationDetail.catalogDescription }}</p>
                  <div class="row">
                    <div class="col-md-3">
                      <strong>Category:</strong> {{ applicationDetail.catalogCategory }}
                    </div>
                    <div class="col-md-3">
                      <strong>Default Port:</strong> {{ applicationDetail.defaultPort }}
                    </div>
                    <div class="col-md-3">
                      <strong class="me-2">Status:</strong> 
                      <span :class="getStatusClass(applicationDetail.applicationStatus)">
                        {{ formatApplicationStatus(applicationDetail.applicationStatus) }}
                      </span>
                    </div>
                    <div class="col-md-3">
                      <strong class="me-2">Health Check:</strong> 
                      <span :class="applicationDetail.healthCheck ? 'text-success' : 'text-danger'">
                        {{ applicationDetail.healthCheck ? 'Healthy' : 'Unhealthy' }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Performance Metrics -->
            <div class="mb-4">
              <h6 class="text-primary">성능 지표</h6>
              <div v-if="!isRuntimeMetricAvailable" class="text-muted small mb-2">
                현재 실행 중인 상태가 아니므로 최신 성능 지표를 표시하지 않습니다.
              </div>
              <div class="row">
                <div class="col-md-3">
                  <div class="card text-center">
                    <div class="card-body">
                      <h5 class="card-title">{{ formatMetricPercent(applicationDetail.cpuUsage) }}</h5>
                      <p class="card-text">CPU 사용률</p>
                    </div>
                  </div>
                </div>
                <div class="col-md-3">
                  <div class="card text-center">
                    <div class="card-body">
                      <h5 class="card-title">{{ formatMetricPercent(applicationDetail.memoryUsage) }}</h5>
                      <p class="card-text">Memory 사용률</p>
                    </div>
                  </div>
                </div>
                <div class="col-md-3">
                  <div class="card text-center">
                    <div class="card-body">
                      <h5 class="card-title">{{ formatMetricBytes(applicationDetail.networkIn) }}</h5>
                      <p class="card-text">네트워크 수신</p>
                    </div>
                  </div>
                </div>
                <div class="col-md-3">
                  <div class="card text-center">
                    <div class="card-body">
                      <h5 class="card-title">{{ formatMetricBytes(applicationDetail.networkOut) }}</h5>
                      <p class="card-text">네트워크 송신</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Policy Recommendation -->
            <div class="mb-4">
              <div class="d-flex justify-content-between align-items-center mb-2">
                <div>
                  <h6 class="text-primary mb-0">정책 추천</h6>
                  <div class="text-muted small">최근 분석 시각: {{ lastPolicyAnalysisAt }}</div>
                </div>
                <div class="btn-list">
                  <button
                    type="button"
                    class="btn btn-outline-primary"
                    :disabled="policyLoading || !applicationDetail.deploymentId || !isPolicyAnalysisAvailable"
                    @click="runPolicyAnalysis">
                    {{ policyLoading ? 'Analyzing...' : 'Analyze' }}
                  </button>
                </div>
              </div>

              <div v-if="policyLoading" class="text-center text-muted py-3">
                <div class="spinner-border spinner-border-sm me-2" role="status"></div>
                정책 추천 정보를 불러오는 중입니다.
              </div>

              <div v-else-if="policyRecommendation" class="border rounded p-3">
                <div v-if="!isPolicyAnalysisAvailable" class="alert alert-light border text-muted py-2 mb-3">
                  운영 종료 또는 미확인 상태의 배포는 정책 추천 분석을 실행하지 않습니다. 아래 내용은 마지막으로 저장된 분석 결과입니다.
                </div>
                <div class="d-flex justify-content-between flex-wrap gap-2 mb-3">
                  <div>
                    <div class="text-muted small">현재 설정 유형</div>
                    <span class="badge bg-secondary">{{ formatResourceType(policyRecommendation.selectedResourceType) }}</span>
                  </div>
                  <div>
                    <div class="text-muted small">추천 운영 유형</div>
                    <span :class="getRecommendationClass(policyRecommendation.recommendedResourceType)">
                      {{ formatResourceType(policyRecommendation.recommendedResourceType) }}
                    </span>
                  </div>
                  <div>
                    <div class="text-muted small">정책 차이</div>
                    <span :class="policyRecommendation.mismatch ? 'badge bg-warning' : 'badge bg-success'">
                      {{ policyRecommendation.mismatch ? '검토 필요' : '일치' }}
                    </span>
                  </div>
                  <div>
                    <div class="text-muted small">분석 신뢰도</div>
                    <span class="badge bg-info">{{ formatConfidence(policyRecommendation.confidence) }}</span>
                  </div>
                  <div>
                    <div class="text-muted small">검토 상태</div>
                    <span class="badge bg-secondary">{{ formatRecommendationStatus(policyRecommendation.status) }}</span>
                  </div>
                </div>

                <div class="table-responsive mb-3">
                  <table class="table table-sm mb-0">
                    <thead>
                      <tr>
                        <th>분석 기간</th>
                        <th>추천 유형</th>
                        <th>데이터 상태</th>
                        <th>신뢰도</th>
                        <th>분석 데이터</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="row in policyPeriodRows"
                        :key="row.period"
                        :class="{ 'policy-period-muted': row.dimmed }">
                        <td>{{ row.period }}d</td>
                        <td>{{ row.profile ? formatResourceType(row.profile.recommendedResourceType) : '-' }}</td>
                        <td>
                          <span :class="getDataStatusClass(row.dataStatus)">
                            {{ formatDataStatus(row.dataStatus) }}
                          </span>
                        </td>
                        <td>{{ row.profile ? formatConfidence(row.profile.confidence) : '-' }}</td>
                        <td>{{ formatDataCoverage(row.profile, row.period) }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <div v-if="operationProfile" class="row g-2 mb-3">
                  <div class="col-md-3">
                    <div class="text-muted small">데이터 상태</div>
                    <span :class="getDataStatusClass(operationProfile.dataStatus)">
                      {{ formatDataStatus(operationProfile.dataStatus) }}
                    </span>
                  </div>
                  <div class="col-md-3">
                    <div class="text-muted small">CPU 상태</div>
                    <span>{{ formatSizingStatus(operationProfile.cpuSizingStatus) }}</span>
                  </div>
                  <div class="col-md-3">
                    <div class="text-muted small">Memory 상태</div>
                    <span>{{ formatSizingStatus(operationProfile.memorySizingStatus) }}</span>
                  </div>
                  <div class="col-md-3">
                    <div class="text-muted small">분석 데이터</div>
                    <span>{{ formatDataCoverage(operationProfile) }}</span>
                  </div>
                </div>

                <div class="mb-3">
                  <div class="text-muted small">검토 항목</div>
                  <span v-for="action in parseActions(policyRecommendation.actions)" :key="action" class="badge bg-light text-dark me-1">
                    {{ formatAction(action) }}
                  </span>
                </div>

                <p class="mb-3">{{ policyRecommendation.message }}</p>

                <div v-if="operationProfile && parseReasons(operationProfile.reasons).length" class="mb-3">
                  <div class="text-muted small">분석 근거</div>
                  <ul class="policy-evidence-list mb-0">
                    <li v-for="reason in parseReasons(operationProfile.reasons)" :key="reason">{{ reason }}</li>
                  </ul>
                </div>

                <div class="btn-list" v-if="policyRecommendation.status === 'OPEN' && isPolicyAnalysisAvailable">
                  <button type="button" class="btn btn-sm btn-success" @click="saveDecision('ACCEPTED')">수락</button>
                  <button type="button" class="btn btn-sm btn-outline-warning" @click="saveDecision('DEFERRED')">보류</button>
                  <button type="button" class="btn btn-sm btn-outline-secondary" @click="saveDecision('IGNORED')">무시</button>
                  <button type="button" class="btn btn-sm btn-outline-danger" @click="saveDecision('REJECTED')">거부</button>
                </div>
              </div>

              <div v-else class="text-muted border rounded p-3">
                정책 추천 정보가 없습니다.
              </div>
            </div>

            <!-- Action History -->
            <div class="mb-4">
              <h6 class="text-primary">Action History</h6>
              <div class="table-responsive">
                <table class="table table-sm">
                  <thead>
                    <tr>
                      <th>Action</th>
                      <th>Timestamp</th>
                      <th>User</th>
                      <th>Status</th>
                      <th>Description</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-if="applicationDetail.operationHistories.length === 0">
                      <td colspan="5" class="text-center text-muted">No action history available</td>
                    </tr>
                    <tr v-for="history in applicationDetail.operationHistories" :key="history.id">
                      <td>{{ history.operationType }}</td>
                      <td>{{ history.executedAt }}</td>
                      <td>{{ history.executedBy || 'system' }}</td>
                      <td>
                        <span :class="getStatusClass(history.status)">
                          {{ formatApplicationStatus(history.status) }}
                        </span>
                      </td>
                      <td>{{ history.detailReason || history.reason }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Error Logs -->
            <div class="mb-4">
              <h6 class="text-primary">Error Logs</h6>
              <div class="table-responsive">
                <table class="table table-sm">
                  <thead>
                    <tr>
                      <th>Timestamp</th>
                      <th>Error Code</th>
                      <th>Severity</th>
                      <th>Module</th>
                      <th>Description</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-if="applicationDetail.errorLogs.length > 0" v-for="log in applicationDetail.errorLogs" :key="log.errorCode">
                      <td>{{ log.loggedAt }}</td>
                      <td>{{ log.errorCode }}</td>
                      <td>
                        <span :class="getSeverityClass(log.severity)">
                          {{ log.severity }}
                        </span>
                      </td>
                      <td>{{ log.module }}</td>
                      <td>{{ log.logMessage }}</td>
                    </tr>

                    <tr v-else>
                      <td colspan="5" class="text-center text-muted">No error logs available</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Deployment History -->
            <div class="mb-4">
              <h6 class="text-primary">Deployment History</h6>
              
              <!-- Basic Deployment Information -->
              <div class="mb-3">
                <h6>Basic Deployment Information</h6>
                <div class="table-responsive">
                  <table class="table table-sm">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Action Type</th>
                        <th>Executed At</th>
                        <th>Executed By</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>{{ applicationDetail.deploymentId }}</td>
                        <td>{{ applicationDetail.actionType }}</td>
                        <td>{{ applicationDetail.executedAt }}</td>
                        <td>{{ applicationDetail.executedBy || 'system' }}</td>
                        <td>
                          <span :class="getStatusClass(applicationDetail.status)">
                            {{ formatApplicationStatus(applicationDetail.status) }}
                          </span>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <!-- Cloud & Cluster Information -->
              <div class="mb-3">
                <h6>Cloud & Cluster Information</h6>
                <div class="table-responsive">
                  <table class="table table-sm">
                    <thead>
                      <tr>
                        <th>Cloud Provider</th>
                        <th>Cloud Region</th>
                        <th>Cluster Name</th>
                        <th>Deployment Type</th>
                        <th>MCI ID</th>
                        <th>UID</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>{{ applicationDetail.cloudProvider }}</td>
                        <td>{{ applicationDetail.cloudRegion }}</td>
                        <td>{{ applicationDetail.clusterName || 'N/A' }}</td>
                        <td>{{ applicationDetail.deploymentType }}</td>
                        <td>{{ applicationDetail.mciId }}</td>
                        <td>{{ applicationDetail.vmId }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <!-- Network & Service Information -->
              <div class="mb-3">
                <h6>Network & Service Information</h6>
                <div class="table-responsive">
                  <table class="table table-sm">
                    <thead>
                      <tr>
                        <th>Namespace</th>
                        <th>Pod Status</th>
                        <th>Public IP</th>
                        <th>Service Port</th>
                        <th>VM ID</th>
                        <th>Catalog ID</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>{{ applicationDetail.namespace }}</td>
                        <td>
                          <span :class="getStatusClass(applicationDetail.podStatus || applicationDetail.applicationStatus)">
                            {{ formatApplicationStatus(applicationDetail.podStatus || applicationDetail.applicationStatus) }}
                          </span>
                        </td>
                        <td>{{ applicationDetail.publicIp }}</td>
                        <td>{{ applicationDetail.servicePort || applicationDetail.defaultPort }}</td>
                        <td>{{ applicationDetail.vmId }}</td>
                        <td>{{ applicationDetail.catalogName }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <div class="mb-3" v-if="applicationDetail.deploymentType === 'K8S'">
                <h6>Deployment Options</h6>
                <div class="table-responsive">
                  <table class="table table-sm">
                    <thead>
                      <tr>
                        <th>Resource Type</th>
                        <th>HPA</th>
                        <th>Min Replicas</th>
                        <th>Max Replicas</th>
                        <th>CPU Threshold</th>
                        <th>Memory Threshold</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>{{ applicationDetail.resourceType || 'N/A' }}</td>
                        <td>
                          <span :class="applicationDetail.hpaEnabled ? 'text-success' : 'text-muted'">
                            {{ applicationDetail.hpaEnabled ? 'Enabled' : 'Disabled' }}
                          </span>
                        </td>
                        <td>{{ applicationDetail.minReplicas || 'N/A' }}</td>
                        <td>{{ applicationDetail.maxReplicas || 'N/A' }}</td>
                        <td>{{ applicationDetail.cpuThreshold ? `${applicationDetail.cpuThreshold}%` : 'N/A' }}</td>
                        <td>{{ applicationDetail.memoryThreshold ? `${applicationDetail.memoryThreshold}%` : 'N/A' }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <!-- Ingress Information -->
              <div class="mb-3" v-if="applicationDetail.ingressEnabled">
                <h6>Ingress Information</h6>
                <div class="table-responsive">
                  <table class="table table-sm">
                    <thead>
                      <tr>
                        <th>Enabled</th>
                        <th>Host</th>
                        <th>Path</th>
                        <th>Class</th>
                        <th>TLS Enabled</th>
                        <th>TLS Secret</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>
                          <span :class="applicationDetail.ingressEnabled ? 'text-success' : 'text-danger'">
                            {{ applicationDetail.ingressEnabled ? 'Yes' : 'No' }}
                          </span>
                        </td>
                        <td>{{ applicationDetail.ingressHost || 'N/A' }}</td>
                        <td>{{ applicationDetail.ingressPath || 'N/A' }}</td>
                        <td>{{ applicationDetail.ingressClass || 'N/A' }}</td>
                        <td>
                          <span :class="applicationDetail.ingressTlsEnabled ? 'text-success' : 'text-danger'">
                            {{ applicationDetail.ingressTlsEnabled ? 'Yes' : 'No' }}
                          </span>
                        </td>
                        <td>{{ applicationDetail.ingressTlsSecret || 'N/A' }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            <!-- Deployment Logs -->
            <div class="mb-4" v-if="applicationDetail.deploymentLogs && applicationDetail.deploymentLogs.length > 0">
              <h6 class="text-primary">Deployment Logs</h6>
              <div class="table-responsive">
                <table class="table table-sm">
                  <thead>
                    <tr>
                      <th>Timestamp</th>
                      <th>Type</th>
                      <th>Message</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="log in applicationDetail.deploymentLogs" :key="log.id">
                      <td>{{ log.loggedAt }}</td>
                      <td>
                        <span class="me-4 mt-2" :class="getLogTypeClass(log.logType)">
                          {{ log.logType }}
                        </span>
                      </td>
                      <td>{{ log.logMessage }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          <div v-else class="text-center text-muted">
            No data available
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal" @click="closeModal">Close</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToast } from 'vue-toastification'
import { IconPackage } from '@tabler/icons-vue'
import {
  analyzePolicyRecommendation,
  getApplicationDetail,
  getOperationProfile,
  getPolicyRecommendation,
  savePolicyRecommendationDecision
} from '@/api/softwareCatalog'
import { toAbsoluteUrl } from '@/common/url'
import {
  getApplicationStatusBadgeClass,
  getApplicationStatusLabel
} from '../applicationStatusDisplay'

interface Props {
  deploymentId: number
  modalId?: string
}

const props = withDefaults(defineProps<Props>(), {
  modalId: 'application-detail-modal'
})
const emit = defineEmits(['close'])

const toast = useToast()
const loading = ref(false)
const policyLoading = ref(false)
const applicationDetail = ref(null as any)
const operationProfile = ref(null as any)
const operationProfiles = ref([] as any[])
const policyRecommendation = ref(null as any)
const logoLoadFailed = ref(false)
const analysisPeriods = [7, 30, 90]

// computed로 deploymentId 관리
const currentDeploymentId = ref(0 as number)

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

const normalizeCatalogName = (name: any) => String(name || '').trim().toLowerCase()

const getInternalCatalogIconUrl = (catalogName: any) => {
  return catalogIconUrlByName[normalizeCatalogName(catalogName)] || ''
}

const applicationLogoUrl = computed(() => {
  const detail = applicationDetail.value
  if (!detail) return ''

  return toAbsoluteUrl(
    getInternalCatalogIconUrl(detail.catalogName) ||
    detail.logoUrlLarge ||
    detail.logoUrlSmall ||
    ''
  )
})

// 강제로 API를 다시 호출하는 메서드
const refreshData = (deploymentId: number) => {
  currentDeploymentId.value = deploymentId
  if (currentDeploymentId.value) {
    loadApplicationDetail()
    loadPolicyRecommendation()
  }
}

defineExpose({
  refreshData
})

const loadApplicationDetail = async () => {
  if (!currentDeploymentId.value) return
  
  loading.value = true
  try {
    logoLoadFailed.value = false
    const { data } = await getApplicationDetail(currentDeploymentId.value)
    if (data) {
      applicationDetail.value = data.integratedInfo
    }
  } catch (error) {
    console.error('Failed to load application detail:', error)
    toast.error('Failed to load application detail')
  } finally {
    loading.value = false
  }
}

const loadPolicyRecommendation = async () => {
  if (!currentDeploymentId.value) return

  policyLoading.value = true
  try {
    const profileRes = await getOperationProfile(currentDeploymentId.value)
    operationProfile.value = profileRes.data || null
    const periodResults = await Promise.all(
      analysisPeriods.map(async (period) => {
        try {
          const res = await getOperationProfile(currentDeploymentId.value, period)
          return res.data || null
        } catch {
          return null
        }
      })
    )
    operationProfiles.value = periodResults.filter(Boolean)

    const recommendationRes = await getPolicyRecommendation(currentDeploymentId.value)
    policyRecommendation.value = recommendationRes.data || null
  } catch (error) {
    console.error('Failed to load policy recommendation:', error)
    operationProfile.value = null
    operationProfiles.value = []
    policyRecommendation.value = null
  } finally {
    policyLoading.value = false
  }
}

const parseDateTime = (value: any) => {
  if (!value) return null

  const parsed = new Date(String(value).replace(' ', 'T'))
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

const formatDateTime = (value: any) => {
  const parsed = value instanceof Date ? value : parseDateTime(value)
  if (!parsed) return '-'

  const pad = (input: number) => String(input).padStart(2, '0')
  return `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())} ${pad(parsed.getHours())}:${pad(parsed.getMinutes())}:${pad(parsed.getSeconds())}`
}

const normalizeStatus = (status: any) => String(status || '').trim().toUpperCase()
const runtimeMetricStatuses = new Set(['RUN', 'RUNNING', 'SUCCESS'])
const terminalRuntimeStatuses = new Set(['UNINSTALL', 'UNINSTALLED', 'NOT_FOUND', 'STOP', 'STOPPED', 'FAILED', 'ERROR'])

const currentRuntimeStatus = computed(() => {
  return normalizeStatus(
    applicationDetail.value?.applicationStatus ||
    applicationDetail.value?.podStatus ||
    applicationDetail.value?.status
  )
})

const hasUninstallAfterDeployment = computed(() => {
  const deployedAt = parseDateTime(applicationDetail.value?.executedAt)
  const histories = Array.isArray(applicationDetail.value?.operationHistories)
    ? applicationDetail.value.operationHistories
    : []

  return histories.some((history: any) => {
    if (normalizeStatus(history?.operationType) !== 'UNINSTALL') return false
    const executedAt = parseDateTime(history?.executedAt || history?.createdAt)
    if (!deployedAt || !executedAt) return true
    return executedAt.getTime() >= deployedAt.getTime()
  })
})

const isRuntimeMetricAvailable = computed(() => {
  if (hasUninstallAfterDeployment.value) return false
  if (terminalRuntimeStatuses.has(currentRuntimeStatus.value)) return false
  return runtimeMetricStatuses.has(currentRuntimeStatus.value)
})

const isPolicyAnalysisAvailable = computed(() => isRuntimeMetricAvailable.value)

const deploymentAgeDays = computed(() => {
  const deployedAt = parseDateTime(applicationDetail.value?.executedAt)
  if (!deployedAt) return 0

  const today = new Date()
  const deployedDate = new Date(deployedAt.getFullYear(), deployedAt.getMonth(), deployedAt.getDate())
  const todayDate = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const diff = todayDate.getTime() - deployedDate.getTime()
  return Math.max(Math.floor(diff / (1000 * 60 * 60 * 24)), 0)
})

const profileByPeriod = computed(() => {
  const map = new Map<number, any>()
  operationProfiles.value.forEach((profile) => {
    const days = getAnalysisDays(profile)
    if (typeof days === 'number') {
      map.set(days, profile)
    }
  })
  return map
})

const policyPeriodRows = computed(() => {
  return analysisPeriods.map((period) => {
    const profile = profileByPeriod.value.get(period) || null
    const eligible = deploymentAgeDays.value >= period
    const dataStatus = profile?.dataStatus || (eligible ? 'PENDING_ANALYSIS' : 'ACCUMULATING')

    return {
      period,
      profile,
      eligible,
      dataStatus,
      dimmed: !eligible || ['INSUFFICIENT_DATA', 'PENDING_ANALYSIS', 'ACCUMULATING'].includes(dataStatus)
    }
  })
})

const lastPolicyAnalysisAt = computed(() => {
  const candidates = [
    policyRecommendation.value?.updatedAt,
    policyRecommendation.value?.createdAt,
    operationProfile.value?.createdAt,
    ...operationProfiles.value.map((profile) => profile?.createdAt)
  ]
    .map(parseDateTime)
    .filter((value): value is Date => Boolean(value))
    .sort((a, b) => b.getTime() - a.getTime())

  return candidates.length ? formatDateTime(candidates[0]) : '분석 이력 없음'
})

const runPolicyAnalysis = async () => {
  if (!currentDeploymentId.value) return
  if (!isPolicyAnalysisAvailable.value) {
    toast.info('현재 실행 중인 배포만 정책 추천을 분석할 수 있습니다.')
    return
  }

  policyLoading.value = true
  try {
    await analyzePolicyRecommendation(currentDeploymentId.value)
    await loadPolicyRecommendation()
    toast.success('Policy recommendation analyzed')
  } catch (error) {
    console.error('Failed to analyze policy recommendation:', error)
    toast.error('Failed to analyze policy recommendation')
  } finally {
    policyLoading.value = false
  }
}

const saveDecision = async (status: string) => {
  if (!policyRecommendation.value?.id) return

  policyLoading.value = true
  try {
    const { data } = await savePolicyRecommendationDecision(policyRecommendation.value.id, {
      status,
      decidedBy: 'admin',
      decisionReason: `Decision saved from application detail modal: ${status}`
    })
    policyRecommendation.value = data
    toast.success('Recommendation decision saved')
  } catch (error) {
    console.error('Failed to save recommendation decision:', error)
    toast.error('Failed to save recommendation decision')
  } finally {
    policyLoading.value = false
  }
}

const getStatusClass = (status: string) => {
  return getApplicationStatusBadgeClass(status)
}

const formatApplicationStatus = (status: string | null | undefined) => {
  return getApplicationStatusLabel(status)
}

const getSeverityClass = (severity: string) => {
  switch (severity?.toLowerCase()) {
    case 'critical':
      return 'badge bg-danger'
    case 'warning':
      return 'badge bg-warning'
    case 'error':
      return 'badge bg-danger'
    default:
      return 'badge bg-info'
  }
}

const getLogTypeClass = (logType: string) => {
  switch (logType?.toLowerCase()) {
    case 'info':
      return 'badge bg-info'
    case 'warning':
      return 'badge bg-warning'
    case 'error':
      return 'badge bg-danger'
    case 'debug':
      return 'badge bg-secondary'
    default:
      return 'badge bg-primary'
  }
}

const getRecommendationClass = (resourceType: string | null | undefined) => {
  switch (resourceType) {
    case 'CPU_INTENSIVE':
      return 'badge bg-primary'
    case 'MEMORY_INTENSIVE':
      return 'badge bg-purple'
    case 'CPU_MEMORY_INTENSIVE':
      return 'badge bg-warning'
    case 'GENERAL_PURPOSE':
      return 'badge bg-success'
    default:
      return 'badge bg-secondary'
  }
}

const formatResourceType = (resourceType: string | null | undefined) => {
  switch (resourceType) {
    case 'CPU_INTENSIVE':
      return 'CPU 중심'
    case 'MEMORY_INTENSIVE':
      return 'Memory 중심'
    case 'CPU_MEMORY_INTENSIVE':
      return 'CPU/Memory 복합'
    case 'GENERAL_PURPOSE':
      return '현행/범용'
    default:
      return resourceType || 'N/A'
  }
}

const formatRecommendationStatus = (status: string | null | undefined) => {
  switch (normalizeStatus(status)) {
    case 'OPEN':
      return '검토 대기'
    case 'ACCEPTED':
      return '수락'
    case 'DEFERRED':
      return '보류'
    case 'IGNORED':
      return '무시'
    case 'REJECTED':
      return '거부'
    default:
      return status || 'N/A'
  }
}

const formatSizingStatus = (status: string | null | undefined) => {
  switch (normalizeStatus(status)) {
    case 'UNDER_PROVISIONED':
      return '용량 부족'
    case 'OVER_PROVISIONED':
      return '사용률 낮음'
    case 'RIGHT_SIZED':
      return '적정'
    default:
      return status || 'N/A'
  }
}

const getDataStatusClass = (dataStatus: string | null | undefined) => {
  switch (dataStatus) {
    case 'SUFFICIENT':
      return 'badge bg-success'
    case 'PARTIAL_DATA':
      return 'badge bg-warning'
    case 'INSUFFICIENT_DATA':
    case 'ACCUMULATING':
    case 'PENDING_ANALYSIS':
      return 'badge bg-secondary'
    default:
      return 'badge bg-secondary'
  }
}

const formatDataStatus = (dataStatus: string | null | undefined) => {
  switch (dataStatus) {
    case 'SUFFICIENT':
      return '충분'
    case 'PARTIAL_DATA':
      return '부분 데이터'
    case 'INSUFFICIENT_DATA':
    case 'ACCUMULATING':
      return '데이터 축적 중'
    case 'PENDING_ANALYSIS':
      return '분석 대기'
    default:
      return dataStatus || 'N/A'
  }
}

const parseActions = (actions: string) => {
  if (!actions) return ['NO_ACTION']
  return actions.split(',').map(action => action.trim()).filter(Boolean)
}

const formatAction = (action: string) => {
  switch (action) {
    case 'INCREASE_CPU':
      return 'CPU 정책 검토'
    case 'INCREASE_MEMORY':
      return 'Memory 정책 검토'
    case 'REVIEW_CPU_MEMORY_POLICY':
      return 'CPU/Memory 복합 검토'
    case 'CHANGE_RESOURCE_TYPE':
      return '운영 유형 검토'
    case 'DOWNSIZE':
      return '현행/하향 검토'
    case 'INVESTIGATE_STABILITY':
      return '안정성 점검'
    case 'NO_ACTION':
      return '현행 유지'
    default:
      return action
  }
}

const formatConfidence = (value: number | null | undefined) => {
  if (value === null || value === undefined) return 'N/A'
  const percent = Math.round(value * 100)
  if (percent >= 80) return `높음 ${percent}%`
  if (percent >= 60) return `보통 ${percent}%`
  if (percent >= 40) return `낮음 ${percent}%`
  return `판단 보류 ${percent}%`
}

const parseReasons = (reasons: string | null | undefined) => {
  if (!reasons) return []
  try {
    const parsed = JSON.parse(reasons)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return reasons.split(',').map(reason => reason.trim()).filter(Boolean)
  }
}

const getAnalysisDays = (profile: any) => {
  if (!profile?.analysisStartDate || !profile?.analysisEndDate) return '-'
  const start = new Date(profile.analysisStartDate)
  const end = new Date(profile.analysisEndDate)
  const diff = Math.round((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24))
  return diff + 1
}

const getAnalysisWindowDays = (profile: any, fallbackPeriod?: number) => {
  const analysisDays = getAnalysisDays(profile)
  if (typeof analysisDays === 'number') return analysisDays

  const validDays = profile?.validDays ?? 0
  const missingDays = profile?.missingDays ?? 0
  const totalDays = validDays + missingDays
  return totalDays || fallbackPeriod || 0
}

const formatDataCoverage = (profile: any, fallbackPeriod?: number) => {
  const validDays = profile?.validDays ?? 0
  const windowDays = getAnalysisWindowDays(profile, fallbackPeriod)

  if (!windowDays) {
    return `${validDays}일 확보`
  }
  return `${validDays}일 확보 / ${windowDays}일 기준`
}

const formatMetricPercent = (value: number | null | undefined) => {
  if (!isRuntimeMetricAvailable.value || value === null || value === undefined) return '-'
  return `${value}%`
}

const formatMetricBytes = (bytes: number | null | undefined) => {
  if (!isRuntimeMetricAvailable.value) return '-'
  return formatBytes(bytes as number)
}

const formatBytes = (bytes: number) => {
  if (bytes === 0) return '0 Bytes'
  if (!bytes) return 'N/A'
  
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const closeModal = () => {
  const modal = document.getElementById(props.modalId)
  if (modal) {
    try {
      // Bootstrap 5를 사용하는 경우
      if ((window as any).bootstrap && (window as any).bootstrap.Modal) {
        const modalInstance = (window as any).bootstrap.Modal.getInstance(modal)
        if (modalInstance) {
          modalInstance.hide()
        } else {
          // 인스턴스가 없으면 새로 생성해서 닫기
          const newModalInstance = new (window as any).bootstrap.Modal(modal)
          newModalInstance.hide()
        }
      } else {
        // Bootstrap이 없거나 다른 경우 직접 모달 닫기
        modal.style.display = 'none'
        modal.classList.remove('show')
        modal.setAttribute('aria-hidden', 'true')
        modal.removeAttribute('aria-modal')
        document.body.classList.remove('modal-open')
        
        // backdrop 제거
        const backdrops = document.querySelectorAll('.modal-backdrop')
        backdrops.forEach(backdrop => backdrop.remove())
        
        // body의 overflow 스타일 복원
        document.body.style.overflow = ''
        document.body.style.paddingRight = ''
      }
    } catch (error) {
      console.error('Error closing modal:', error)
      // 에러 발생 시 강제로 모달 닫기
      modal.style.display = 'none'
      modal.classList.remove('show')
      document.body.classList.remove('modal-open')
      const backdrops = document.querySelectorAll('.modal-backdrop')
      backdrops.forEach(backdrop => backdrop.remove())
    }
  }
  
  // 데이터 초기화
  applicationDetail.value = null
  operationProfile.value = null
  operationProfiles.value = []
  policyRecommendation.value = null
  emit('close')
}


</script>

<style scoped>
.table th {
  font-weight: 600;
  background-color: #f8f9fa;
}

.table-responsive {
  border-radius: 6px;
  border: 1px solid #dee2e6;
}

.badge {
  font-size: 0.75rem;
}

h6 {
  color: #495057;
  font-weight: 600;
  margin-bottom: 0.75rem;
}

.text-primary {
  color: #0d6efd !important;
}

.card {
  border: 1px solid #dee2e6;
  border-radius: 8px;
  margin-bottom: 1rem;
}

.card-body {
  padding: 1rem;
}

.card-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.card-text {
  color: #6c757d;
  font-size: 0.875rem;
  margin-bottom: 0;
}

.img-fluid {
  border-radius: 8px;
}

.application-detail-logo,
.application-detail-logo-fallback {
  width: 80px;
  height: 80px;
}

.application-detail-logo {
  object-fit: contain;
}

.application-detail-logo-fallback {
  border: 1px solid #dee2e6;
  border-radius: 8px;
  color: #6c757d;
  background-color: #f8f9fa;
}

.row {
  margin-bottom: 1rem;
}

.col-md-3 strong,
.col-md-10 strong {
  color: #495057;
}

.policy-evidence-list {
  padding-left: 1rem;
  color: #495057;
  font-size: 0.8125rem;
}

.policy-period-muted {
  color: #6c757d;
  opacity: 0.62;
}

.policy-period-muted .badge {
  opacity: 0.82;
}
</style>
