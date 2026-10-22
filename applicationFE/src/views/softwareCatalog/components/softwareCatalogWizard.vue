<template>
  <div class="modal fade" id="modal-wizard" tabindex="-1" ref="wizardModal">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">{{ props.mode === 'update' ? 'Application Update' : 'Application Registration' }}</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close" :disabled="isSubmitting" />
        </div>

        <div class="modal-body" style="max-height: calc(100vh - 200px);overflow-y: auto;">
          <ul class="nav nav-tabs mb-3">
            <li class="nav-item">
              <a class="nav-link" :class="{ active: currentStep === 1 }" href="javascript:void(0);" @click="onClickStep(1)">1. {{ props.mode === 'update' ? 'Package' : 'Source & Package' }}</a>
            </li>
            <li class="nav-item">
              <a class="nav-link" :class="{ active: currentStep === 2 }" href="javascript:void(0);" @click="onClickStep(2)">2. General</a>
            </li>
            <li class="nav-item">
              <a class="nav-link" :class="{ active: currentStep === 3 }" href="javascript:void(0);" @click="onClickStep(3)">3. Resource Requirements</a>
            </li>
            <li class="nav-item">
              <a class="nav-link" :class="{ active: currentStep === 4 }" href="javascript:void(0);" @click="onClickStep(4)">4. Network</a>
            </li>
          </ul>

          <div v-show="currentStep === 1">
            <div v-if="props.mode !== 'update'" class="mb-4">
              <label class="form-label required">Registration Source</label>
              <div class="row g-3">
                <div class="col-md-6">
                  <label class="source-option" :class="{ selected: registrationSource === 'existing' }" for="sourceExisting">
                    <input
                      id="sourceExisting"
                      v-model="registrationSource"
                      class="form-check-input"
                      type="radio"
                      name="registrationSource"
                      value="existing"
                      :disabled="isSubmitting">
                    <span>
                      <strong>Existing Package</strong>
                      <small>Register a package already available in MCMP.</small>
                    </span>
                  </label>
                </div>
                <div class="col-md-6">
                  <label class="source-option" :class="{ selected: registrationSource === 'public' }" for="sourcePublic">
                    <input
                      id="sourcePublic"
                      v-model="registrationSource"
                      class="form-check-input"
                      type="radio"
                      name="registrationSource"
                      value="public"
                      :disabled="isSubmitting">
                    <span>
                      <strong>Public Registry</strong>
                      <small>Search, import, and register in one flow.</small>
                    </span>
                  </label>
                </div>
              </div>
            </div>

            <div class="mb-3">
              <label class="form-label required">Target</label>
              <div class="d-flex align-items-center">
                <div class="form-check me-3">
                  <input class="form-check-input" type="radio" name="target" value="VM" v-model="catalogDto.target" id="targetVM" :disabled="props.mode === 'update' || isSubmitting"/>
                  <label class="form-check-label" for="targetVM">VM</label>
                </div>
                <div class="form-check">
                  <input class="form-check-input" type="radio" name="target" value="K8S" v-model="catalogDto.target" id="targetK8S" :disabled="props.mode === 'update' || isSubmitting" />
                  <label class="form-check-label" for="targetK8S">K8S</label>
                </div>
              </div>
            </div>

            <template v-if="usesExistingPackage">
              <div class="mb-3">
                <label class="form-label required">Category</label>
                <select class="form-select" v-model="catalogDto.category" :disabled="props.mode === 'update'">
                  <option value="">Select Category</option>
                  <option v-for="category in categoryList" :value="category.value" :key="category.key">{{ category.value }}</option>
                </select>
              </div>

              <div class="row g-3">
                <div class="col-md-6">
                  <label class="form-label required">Package</label>
                  <select class="form-select" v-model="catalogDto.packageName" :disabled="props.mode === 'update'">
                    <option value="">Select Package</option>
                    <option v-for="data in packageList" :value="data.value" :key="data.key">{{ data.value }}</option>
                  </select>
                </div>
                <div class="col-md-6">
                  <label class="form-label required">Version</label>
                  <select class="form-select" v-model="catalogDto.version" :disabled="props.mode === 'update'">
                    <option value="">Select Version</option>
                    <option v-for="version in versionList" :value="version.value" :key="version.key">{{ version.value }}</option>
                  </select>
                </div>
              </div>
            </template>

            <template v-else>
              <div class="alert alert-info py-2 mb-3" role="status">
                <strong>{{ registryProviderName }}</strong> will be searched for the selected target.
                The selected package is copied to Nexus when you complete registration.
                <span v-if="catalogDto.target === 'K8S'">Container image locations defined by the chart are preserved.</span>
              </div>

              <form class="mb-3" @submit.prevent="searchPublicRegistry">
                <label class="form-label required" for="registrySearchInput">Search {{ registryProviderName }}</label>
                <div class="input-group">
                  <input
                    id="registrySearchInput"
                    v-model="registrySearchKeyword"
                    type="search"
                    class="form-control"
                    :placeholder="registrySearchPlaceholder"
                    :disabled="registrySearchLoading || isSubmitting">
                  <button class="btn btn-outline-primary" type="submit" :disabled="registrySearchLoading || isSubmitting">
                    {{ registrySearchLoading ? 'Searching...' : 'Search' }}
                  </button>
                </div>
                <div v-if="registrySearchError" class="text-danger small mt-2">{{ registrySearchError }}</div>
              </form>

              <div v-if="registrySearchLoading" class="text-center text-muted py-4">Searching {{ registryProviderName }}...</div>
              <div v-else-if="registrySearchPerformed && registrySearchResults.length === 0" class="text-muted py-3">
                No matching packages found.
              </div>
              <div v-else-if="registrySearchResults.length > 0" class="registry-results list-group mb-3">
                <button
                  v-for="(result, index) in registrySearchResults"
                  :key="getRegistryResultKey(result, index)"
                  type="button"
                  class="list-group-item list-group-item-action"
                  :class="{ active: isRegistryResultSelected(result) }"
                  :disabled="registryVersionLoading || isSubmitting"
                  @click="selectRegistryPackage(result)">
                  <div class="d-flex align-items-start gap-3">
                    <img
                      v-if="getRegistryLogo(result)"
                      :src="getRegistryLogo(result)"
                      class="rounded registry-logo"
                      alt=""
                      width="40"
                      height="40">
                    <div class="flex-fill text-start">
                      <div class="fw-semibold">{{ result?.name || result?.packageId || 'Unnamed package' }}</div>
                      <div class="small registry-description">
                        {{ truncateText(getRegistryDescription(result), 120) }}
                      </div>
                    </div>
                  </div>
                </button>
              </div>

              <div v-if="selectedRegistryPackage" class="row g-3">
                <div class="col-md-6">
                  <label class="form-label required">Version</label>
                  <select class="form-select" v-model="catalogDto.version" :disabled="registryVersionLoading || isSubmitting">
                    <option value="">{{ registryVersionLoading ? 'Loading versions...' : 'Select Version' }}</option>
                    <option v-for="version in registryVersionList" :value="version.value" :key="version.key">{{ version.value }}</option>
                  </select>
                </div>
                <div class="col-md-6">
                  <label class="form-label required">Category</label>
                  <select class="form-select" v-model="catalogDto.category" :disabled="isSubmitting">
                    <option value="">Select Category</option>
                    <option v-for="category in publicCategoryList" :value="category.value" :key="category.key">{{ category.value }}</option>
                  </select>
                </div>
              </div>
            </template>
          </div>

          <div v-show="currentStep === 2">
            <div class="mb-3">
              <label class="form-label required">Application Name</label>
              <input type="text" class="form-control" v-model="catalogDto.name" placeholder="Application name" />
            </div>
            <div class="mb-3">
              <label class="form-label required">Summary</label>
              <input type="text" class="form-control" v-model="catalogDto.summary" placeholder="Application summary" />
            </div>
            <div class="mb-3">
              <label class="form-label required">Description</label>
              <textarea class="form-control" rows="4" v-model="catalogDto.description" placeholder="Application description" />
            </div>
            <div class="mb-3">
              <label class="form-label">Reference</label>
              <div class="row g-2 mb-2" v-for="(ref, idx) in refData" :key="idx">
                <div class="col-5">
                  <select class="form-select" v-model="ref.refType">
                    <option value="URL">URL</option>
                    <option value="IMAGE">IMAGE</option>
                    <option value="HOMEPAGE">HOMEPAGE</option>
                    <option value="TAG">TAG</option>
                    <option value="CAPABILITY">CAPABILITY</option>
                    <option value="ETC">ETC</option>
                  </select>
                </div>
                <div class="col-6">
                  <input type="text" class="form-control" v-model="ref.refValue" placeholder="Ref Value" />
                </div>
                <div class="col-1 d-flex gap-2">
                  <!-- <button type="button" class="btn btn-primary btn-sm" @click="addRef">+</button> -->
                  <button type="button" class="btn btn-outline-danger btn-sm w-100 cursor-pointer" @click="removeRef(idx)" :disabled="refData.length <= 1">-</button>
                </div>
              </div>
              <div class="form-text cursor-pointer" @click="addRef" style="color: gray;">+ Add Reference</div>

              <!-- <div class="row g-2 mb-2">
                <div class="cursor-pointer col-3" @click="addRef" style="color: gray;">
                  <IconPlus class="icon icon-tabler icon-tabler-plus m-0" size="24"/>
                  <label class="cursor-pointer"> Add Reference</label>
                </div>
              </div> -->
            </div>
          </div>

          <div v-show="currentStep === 3">
            <div class="row">
              <div class="col-md-6">
                <label class="form-label">CPU</label>
                <div class="row">
                  <div class="col-6">
                    <label class="form-label text-muted small">Minimum</label>
                    <div class="input-group">
                      <input type="number" min="0" step="0.1" class="form-control" v-model.number="catalogDto.minCpu" placeholder="1" />
                      <span class="input-group-text">Cores</span>
                    </div>
                  </div>
                  <div class="col-6">
                    <label class="form-label text-muted small">Recommended</label>
                    <div class="input-group">
                      <input type="number" min="0" step="0.1" class="form-control" v-model.number="catalogDto.recommendedCpu" placeholder="2" />
                      <span class="input-group-text">Cores</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="row mt-3">
              <div class="col-md-6">
                <label class="form-label">Memory</label>
                <div class="row">
                  <div class="col-6">
                    <label class="form-label text-muted small">Minimum</label>
                    <div class="input-group">
                      <input type="number" min="0" step="0.1" class="form-control" v-model.number="catalogDto.minMemory" placeholder="4" />
                      <span class="input-group-text">GB</span>
                    </div>
                  </div>
                  <div class="col-6">
                    <label class="form-label text-muted small">Recommended</label>
                    <div class="input-group">
                      <input type="number" min="0" step="0.1" class="form-control" v-model.number="catalogDto.recommendedMemory" placeholder="8" />
                      <span class="input-group-text">GB</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="row mt-3">
              <div class="col-md-6">
                <label class="form-label">Storage</label>
                <div class="row">
                  <div class="col-6">
                    <label class="form-label text-muted small">Minimum</label>
                    <div class="input-group">
                      <input type="number" min="0" class="form-control" v-model.number="catalogDto.minDisk" placeholder="10" />
                      <span class="input-group-text">GB</span>
                    </div>
                  </div>
                  <div class="col-6">
                    <label class="form-label text-muted small">Recommended</label>
                    <div class="input-group">
                      <input type="number" min="0" class="form-control" v-model.number="catalogDto.recommendedDisk" placeholder="20" />
                      <span class="input-group-text">GB</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="card mt-3" v-if="catalogDto.target === 'K8S'">
              <div class="card-body">
                <div class="d-flex align-items-center mb-2">
                  <label class="form-check-label me-2">K8S HPA</label>
                  <div class="form-check form-switch">
                    <input class="form-check-input" type="checkbox" v-model="catalogDto.hpaEnabled" />
                  </div>
                </div>
                <div class="row">
                  <div class="col-md-3">
                    <label class="form-label">minReplicas</label>
                    <input type="number" min="1" class="form-control" v-model.number="catalogDto.minReplicas" :disabled="!catalogDto.hpaEnabled" placeholder="1" />
                  </div>
                  <div class="col-md-3">
                    <label class="form-label">maxReplicas</label>
                    <input type="number" min="1" class="form-control" v-model.number="catalogDto.maxReplicas" :disabled="!catalogDto.hpaEnabled" placeholder="10" />
                  </div>
                  <div class="col-md-3">
                    <label class="form-label">CPU (%)</label>
                    <input type="number" min="1" max="100" class="form-control" v-model.number="catalogDto.cpuThreshold" :disabled="!catalogDto.hpaEnabled" placeholder="80" />
                  </div>
                  <div class="col-md-3">
                    <label class="form-label">Memory (%)</label>
                    <input type="number" min="1" max="100" class="form-control" v-model.number="catalogDto.memoryThreshold" :disabled="!catalogDto.hpaEnabled" placeholder="80" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-show="currentStep === 4">
            <!-- VM 타겟일 때 포트 매핑 -->
            <div v-if="catalogDto.target === 'VM'">
              <div class="card">
                <div class="card-header">
                  <h6 class="card-title">Port Mapping</h6>
                </div>
                <div class="card-body">
                  <div class="mb-3">
                    <label class="form-label">Port</label>
                    <input type="number" min="1" max="65535" class="form-control" v-model.number="catalogDto.defaultPort" placeholder="80" />
                    <!-- <div class="form-text cursor-pointer" @click="addPortMapping" style="color: gray;">+ Add Port Mapping</div> -->

                    <!-- <div class="row g-2 mb-2">
                      <div class="cursor-pointer col-3" @click="addPortMapping" style="color: gray;">
                        <IconPlus class="icon icon-tabler icon-tabler-plus m-0" size="24"/>
                        <label class="cursor-pointer"> Add Port Mapping</label>
                      </div>
                    </div> -->
                  </div>
                </div>
              </div>
            </div>

            <!-- K8S 타겟일 때 포트 매핑 -->
            <div v-if="catalogDto.target === 'K8S'">
              <div class="card">
                <div class="card-header">
                  <h6 class="card-title">Port Mapping</h6>
                </div>
                <div class="card-body">
                  <div class="mb-3">
                    <label class="form-label">Port</label>
                    <div class="row g-2 mb-2" v-for="(port, idx) in portMappings" :key="idx">
                      <div class="col-4">
                        <label class="form-label small">Target Port</label>
                        <input type="number" min="1" max="65535" class="form-control" v-model.number="port.targetPort" placeholder="80" />
                      </div>
                      <div class="col-3">
                        <label class="form-label small">Protocol</label>
                        <select class="form-select" v-model="port.protocol">
                          <option value="TCP">TCP</option>
                          <option value="UDP">UDP</option>
                          <option value="SCTP">SCTP</option>
                        </select>
                      </div>
                      <div class="col-4">
                        <label class="form-label small">Host Port</label>
                        <input type="number" min="1" max="65535" class="form-control" v-model.number="port.hostPort" placeholder="8080" />
                      </div>
                      <div class="col-1 d-flex align-items-end gap-2">
                        <!-- <button type="button" class="btn btn-primary btn-sm" @click="addPortMapping">+</button> -->
                        <button type="button" class="btn btn-outline-danger btn-sm w-100 cursor-pointer" @click="removePortMapping(idx)" :disabled="portMappings.length <= 1">-</button>
                      </div>
                    </div>
                    <div class="form-text cursor-pointer" @click="addPortMapping" style="color: gray;">+ Add Port Mapping</div>

                    <!-- <div class="row g-2 mb-2">
                      <div class="cursor-pointer col-3" @click="addPortMapping" style="color: gray;">
                        <IconPlus class="icon icon-tabler icon-tabler-plus m-0" size="24"/>
                        <label class="cursor-pointer"> Add Port Mapping</label>
                      </div>
                    </div> -->
                  </div>
                </div>
              </div>

              <!-- <div class="card mt-3">
                <div class="card-body">
                  <div class="d-flex align-items-center mb-3">
                    <label class="form-check-label me-2">Ingress Configuration</label>
                    <div class="form-check form-switch">
                      <input class="form-check-input" type="checkbox" v-model="catalogDto.ingressEnabled" />
                    </div>
                  </div>
                  <div v-if="catalogDto.ingressEnabled">
                    <div class="mb-3">
                      <label class="form-label">Ingress Path</label>
                      <input type="text" class="form-control" v-model="catalogDto.ingressUrl" placeholder="/" />
                    </div>
                  </div>
                </div>
              </div> -->
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button type="button" class="btn btn-link link-secondary" data-bs-dismiss="modal" :disabled="isSubmitting" @click="reset">
            Cancel
          </button>
          <div class="ms-auto d-flex gap-2">
            <button class="btn btn-outline-secondary" :disabled="currentStep === 1 || isSubmitting" @click="prevStep">Prev</button>
            <button class="btn btn-primary" v-if="currentStep < 4" :disabled="!canGoNext || isSubmitting" @click="nextStep">Next</button>
            <button class="btn btn-primary" v-else :disabled="!canGoNext || isSubmitting" @click="props.mode === 'update' ? update() : create()">{{ submitButtonLabel }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useToast } from 'vue-toastification'
import {
  createSoftwareCatalog,
  updateSoftwareCatalog,
  getSoftwareCaltalogDetail,
  getPackageList,
  getCategoryList,
  getVersionList,
  searchDockerhub,
  searchArtifacthubhub,
  getApplicationTagForDockerHub,
  getApplicationTagForArtifactHub,
  upLoadDockerHubApplication,
  upLoadArtifactHubApplication
} from '../../../api/softwareCatalog'

interface Props {
  show?: boolean
  mode?: string
}
const props = defineProps<Props>()
const emit = defineEmits(['created', 'updated'])
const toast = useToast()


const currentStep = ref(1)
// 초기값 설정 중인지 여부를 나타내는 플래그
const isInitializing = ref(false)
// 모달이 열렸는지 여부를 나타내는 플래그
const isModalOpen = ref(false)
const catalogId = ref(0 as any)
const catalogInfo = ref({} as any)

type RegistrationSource = 'existing' | 'public'

const DEFAULT_CATEGORY_OPTIONS = [
  'AI_MACHINE_LEARNING',
  'DATABASE',
  'INTEGRATION_AND_DELIVERY',
  'MONITORING_AND_LOGGING',
  'NETWORKING',
  'SECURITY',
  'STORAGE',
  'STREAMING_AND_MESSAGING'
]

const CATEGORY_ID_MAP: Record<string, string> = {
  '1': 'AI_MACHINE_LEARNING',
  '2': 'DATABASE',
  '3': 'INTEGRATION_AND_DELIVERY',
  '4': 'MONITORING_AND_LOGGING',
  '5': 'NETWORKING',
  '6': 'SECURITY',
  '7': 'STORAGE',
  '8': 'STREAMING_AND_MESSAGING'
}

const CATEGORY_NAME_MAP: Record<string, string> = {
  'AI / MACHINE LEARNING': 'AI_MACHINE_LEARNING',
  'AI MACHINE LEARNING': 'AI_MACHINE_LEARNING',
  'DATABASE': 'DATABASE',
  'INTEGRATION AND DELIVERY': 'INTEGRATION_AND_DELIVERY',
  'MONITORING AND LOGGING': 'MONITORING_AND_LOGGING',
  'MONITORING & LOGGING': 'MONITORING_AND_LOGGING',
  'NETWORKING': 'NETWORKING',
  'SECURITY': 'SECURITY',
  'STORAGE': 'STORAGE',
  'STREAMING AND MESSAGING': 'STREAMING_AND_MESSAGING'
}

const registrationSource = ref<RegistrationSource>('existing')
const registrySearchKeyword = ref('')
const registrySearchResults = ref<any[]>([])
const registrySearchLoading = ref(false)
const registrySearchPerformed = ref(false)
const registrySearchError = ref('')
const selectedRegistryPackage = ref<any | null>(null)
const registryVersionList = ref<{ key: string, value: string }[]>([])
const registryVersionLoading = ref(false)
const publicCategoryList = ref<{ key: string, value: string }[]>(
  DEFAULT_CATEGORY_OPTIONS.map(value => ({ key: value, value }))
)
const isSubmitting = ref(false)
const registryImporting = ref(false)
const importedRegistryKey = ref('')

// ------------------------------------------------------------ Life Cycle ------------------------------------------------------------
const wizardModal = ref<HTMLElement | null>(null)
const isDataLoaded = ref(false) // 데이터 로딩 중복 방지 플래그

onMounted(() => {
  if (wizardModal.value) {
    wizardModal.value.addEventListener('show.bs.modal', handleModalShow as EventListener)
    wizardModal.value.addEventListener('hide.bs.modal', handleModalHide as EventListener)
  }
  _getCategoryList()
})
onBeforeUnmount(() => {
  if (wizardModal.value) {
    wizardModal.value.removeEventListener('show.bs.modal', handleModalShow as EventListener)
    wizardModal.value.removeEventListener('hide.bs.modal', handleModalHide as EventListener)
  }
})

// props로 전달받던 데이터는 이제 메서드를 통해 설정

// ------------------------------------------------------------ Life Cycle ------------------------------------------------------------

// ------------------------------------------------------------ Modal Event ------------------------------------------------------------
// modal open 이벤트에서 값 초기화
const handleModalShow = () => {
  isModalOpen.value = true
  // 모달이 열릴 때는 항상 reset부터 수행
  if (props.mode === 'new') {
    reset()
  }
  
  // 수정 모드이고 catalogId가 있으면 데이터 로드
  // if (props.mode === 'update' && catalogId.value) {
  //   loadCatalogDataWithCategoryInit()
  // }
}

// 모달 닫힘 이벤트 핸들러
const handleModalHide = () => {
  isModalOpen.value = false
  isDataLoaded.value = false
}
// ------------------------------------------------------------ Modal Event ------------------------------------------------------------

// ------------------------------------------------------------ Data ------------------------------------------------------------
const catalogDto = ref({
  // 0. Common
  id: null,
  
  // 1. Package
  target: 'VM', // VM 또는 K8S
  sourceType: 'DOCKERHUB', // DOCKERHUB, ARTIFACTHUB 등
  category: '',
  packageName: '', // 새로 추가된 필드
  version: '', // 새로 추가된 필드
  packageInfo: null,
  helmChart: null,

  // 2. General
  name: '',
  summary: '',
  description: '',
  logoUrlLarge: '',
  logoUrlSmall: '',
  catalogRefs: [] as any[],

  // 3. Resource Requirements
  minCpu: 0,
  recommendedCpu: 0,
  minMemory: 0,
  recommendedMemory: 0,
  minDisk: 0,
  recommendedDisk: 0,
  hpaEnabled: false,
  minReplicas: 1,
  maxReplicas: 10,
  cpuThreshold: 80,
  memoryThreshold: 80,

  // 4. Network
  ports: [] as any[],
  ingressEnabled: false,
  ingressUrl: '',
  defaultPort: 80, // 임시 필드

  // 5. etc
  registeredById: null,
  createdAt: null,
  updatedAt: null,
} as any)

const refData = ref([
  { refId: 0, refValue: '', refDesc: '', refType: 'URL' }
] as any[])

const portMappings = ref([
  { targetPort: 80, hostPort: 8080, protocol: 'TCP' }
] as any[])

// ------------------------------------------------------------ Data ------------------------------------------------------------

// ------------------------------------------------------------ Computed ------------------------------------------------------------
const usesExistingPackage = computed(() => props.mode === 'update' || registrationSource.value === 'existing')
const registryProviderName = computed(() => catalogDto.value.target === 'VM' ? 'Docker Hub' : 'Artifact Hub')
const registrySearchPlaceholder = computed(() => catalogDto.value.target === 'VM'
  ? 'Search official container images'
  : 'Search official Helm charts')

const submitButtonLabel = computed(() => {
  if (props.mode === 'update') return isSubmitting.value ? 'Updating...' : 'Update'
  if (registryImporting.value) return 'Importing...'
  if (isSubmitting.value) return 'Creating...'
  return registrationSource.value === 'public' ? 'Import & Create' : 'Create'
})

const canGoNext = computed(() => {
  if (currentStep.value === 1) {
    return catalogDto.value.target && 
      catalogDto.value.category && 
      catalogDto.value.packageName && 
      catalogDto.value.version
  }
  if (currentStep.value === 2) {
    return catalogDto.value.name.trim().length > 0 && 
      catalogDto.value.summary.trim().length > 0 && 
      catalogDto.value.description.trim().length > 0
  }
  if (currentStep.value === 3) {
    return catalogDto.value.minCpu > 0 && 
      catalogDto.value.minMemory > 0 && 
      catalogDto.value.minDisk > 0 && 
      catalogDto.value.recommendedCpu > 0 && 
      catalogDto.value.recommendedMemory > 0 && 
      catalogDto.value.recommendedDisk > 0
  }
  if(currentStep.value === 3 && catalogDto.value.hpaEnabled) {
    return catalogDto.value.minReplicas > 0 && 
      catalogDto.value.maxReplicas > 0 && 
      catalogDto.value.cpuThreshold > 0 && 
      catalogDto.value.memoryThreshold > 0
  }
  console.log(portMappings.value)
  if(currentStep.value === 3 && catalogDto.value.target === 'VM') {
    return catalogDto.value.defaultPort > 0
  }
  if (currentStep.value === 4 && catalogDto.value.target === 'K8S') {
    console.log(portMappings.value.length)
    if(portMappings.value.length > 0) {
      return portMappings.value.every((port: any) => port.targetPort > 0 && port.hostPort > 0 && port.protocol)
    } else {
      return false
    }
  }
  if(currentStep.value === 4 && catalogDto.value.ingressEnabled) {
    return catalogDto.value.ingressUrl.trim().length > 0
  }
  return true
})
// ------------------------------------------------------------ Computed ------------------------------------------------------------

// ------------------------------------------------------------ Method ------------------------------------------------------------
const nextStep = () => {
  if (!canGoNext.value) return
  if (currentStep.value < 4) currentStep.value += 1
}

const prevStep = () => {
  if (currentStep.value > 1) currentStep.value -= 1
}

const resetRegistrySelection = (clearKeyword = true) => {
  if (clearKeyword) registrySearchKeyword.value = ''
  registrySearchResults.value = []
  registrySearchPerformed.value = false
  registrySearchError.value = ''
  selectedRegistryPackage.value = null
  registryVersionList.value = []
  registrySearchLoading.value = false
  registryVersionLoading.value = false
  registryImporting.value = false
  importedRegistryKey.value = ''
  delete catalogDto.value.packageInfoId
  delete catalogDto.value.helmChartId
}

const normalizeCategoryValue = (category: any): string => {
  if (category === null || category === undefined) return ''

  if (typeof category === 'object') {
    return normalizeCategoryValue(
      category.value ||
      category.key ||
      category.name ||
      category.displayName ||
      category.display_name ||
      category.category
    )
  }

  const rawValue = String(category).trim()
  if (!rawValue) return ''
  if (CATEGORY_ID_MAP[rawValue]) return CATEGORY_ID_MAP[rawValue]

  const normalizedDisplayName = rawValue
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toUpperCase()

  if (CATEGORY_NAME_MAP[normalizedDisplayName]) {
    return CATEGORY_NAME_MAP[normalizedDisplayName]
  }

  return rawValue
    .replace(/&/g, 'AND')
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .replace(/_+/g, '_')
    .toUpperCase()
}

const mergePublicCategories = (categories: any[] = []) => {
  const categoryMap = new Map<string, { key: string, value: string }>()
  ;[...DEFAULT_CATEGORY_OPTIONS, ...publicCategoryList.value, ...categories].forEach(category => {
    const normalized = normalizeCategoryValue(category?.value || category?.key || category)
    if (normalized) categoryMap.set(normalized, { key: normalized, value: normalized })
  })
  publicCategoryList.value = Array.from(categoryMap.values()).sort((a, b) => a.value.localeCompare(b.value))
}

const loadPublicCategories = async () => {
  mergePublicCategories()
  const results = await Promise.allSettled([
    getCategoryList({ target: 'DOCKER', availableOnly: false }),
    getCategoryList({ target: 'HELM', availableOnly: false })
  ])
  const categories = results.flatMap(result => result.status === 'fulfilled' ? (result.value.data || []) : [])
  mergePublicCategories(categories)
}

const getRegistryResultKey = (result: any, index = 0) =>
  String(result?.id || result?.packageId || `${result?.repository?.name || 'registry'}-${result?.name || index}`)

const isRegistryResultSelected = (result: any) =>
  Boolean(selectedRegistryPackage.value) &&
  getRegistryResultKey(selectedRegistryPackage.value) === getRegistryResultKey(result)

const getRegistryLogo = (result: any) => result?.logo_url?.small || result?.logo_url?.large || ''
const getRegistryDescription = (result: any) => result?.short_description || result?.description || ''
const truncateText = (value: any, maxLength: number) => {
  const text = String(value || '')
  return text.length > maxLength ? `${text.substring(0, maxLength)}...` : text
}

const searchPublicRegistry = async () => {
  const keyword = registrySearchKeyword.value.trim()
  registrySearchError.value = ''
  registrySearchPerformed.value = false
  registrySearchResults.value = []
  selectedRegistryPackage.value = null
  registryVersionList.value = []
  catalogDto.value.packageName = ''
  catalogDto.value.version = ''

  if (!keyword) {
    registrySearchError.value = 'Enter a search keyword.'
    return
  }

  registrySearchKeyword.value = keyword
  registrySearchLoading.value = true
  try {
    const { data } = catalogDto.value.target === 'VM'
      ? await searchDockerhub(keyword)
      : await searchArtifacthubhub(keyword)
    const results = catalogDto.value.target === 'VM' ? data?.results : data?.packages
    registrySearchResults.value = (Array.isArray(results) ? results : []).filter(Boolean).slice(0, 8)
    registrySearchPerformed.value = true
  } catch (error) {
    console.log(error)
    registrySearchError.value = `Unable to search ${registryProviderName.value}.`
  } finally {
    registrySearchLoading.value = false
  }
}

const selectRegistryPackage = async (result: any) => {
  selectedRegistryPackage.value = result
  catalogDto.value.packageName = result?.name || ''
  catalogDto.value.version = ''
  catalogDto.value.sourceType = catalogDto.value.target === 'VM' ? 'DOCKERHUB' : 'ARTIFACTHUB'
  registryVersionList.value = []
  registrySearchError.value = ''
  importedRegistryKey.value = ''

  const description = getRegistryDescription(result)
  catalogDto.value.name = result?.name || catalogDto.value.name
  catalogDto.value.summary = description || result?.name || ''
  catalogDto.value.description = description || result?.name || ''
  catalogDto.value.logoUrlLarge = result?.logo_url?.large || ''
  catalogDto.value.logoUrlSmall = result?.logo_url?.small || ''

  const recommendedCategory = normalizeCategoryValue(result?.category || result?.categories?.[0])
  mergePublicCategories(recommendedCategory ? [recommendedCategory] : [])
  catalogDto.value.category = recommendedCategory

  const selectedKey = getRegistryResultKey(result)
  registryVersionLoading.value = true
  try {
    const { data } = catalogDto.value.target === 'VM'
      ? await getApplicationTagForDockerHub({ path: result?.id || '' })
      : await getApplicationTagForArtifactHub({
          kind: 'helm',
          repository: result?.repository?.name || '',
          packageName: result?.name || ''
        })

    if (getRegistryResultKey(selectedRegistryPackage.value) !== selectedKey) return

    const versions = (Array.isArray(data) ? data : [])
      .map((item: any) => catalogDto.value.target === 'VM' ? item?.name : item?.version)
      .filter(Boolean)

    if (versions.length === 0 && result?.version) versions.push(result.version)
    registryVersionList.value = Array.from(new Set(versions)).map(value => ({ key: value, value }))
    if (registryVersionList.value.length === 1) {
      catalogDto.value.version = registryVersionList.value[0].value
    }
  } catch (error) {
    console.log(error)
    if (getRegistryResultKey(selectedRegistryPackage.value) !== selectedKey) return
    if (result?.version) {
      registryVersionList.value = [{ key: result.version, value: result.version }]
      catalogDto.value.version = result.version
    } else {
      registrySearchError.value = 'Unable to retrieve package versions.'
    }
  } finally {
    if (getRegistryResultKey(selectedRegistryPackage.value) === selectedKey) {
      registryVersionLoading.value = false
    }
  }
}

const reset = () => {
  currentStep.value = 1
  registrationSource.value = 'existing'
  isSubmitting.value = false
  resetRegistrySelection()
  publicCategoryList.value = DEFAULT_CATEGORY_OPTIONS.map(value => ({ key: value, value }))
  catalogDto.value = {
    // 0. Common
    id: null,
    
    // 1. Package
    target: 'VM',
    sourceType: 'DOCKERHUB',
    category: '',
    packageName: '',
    version: '',
    packageInfo: null,
    helmChart: null,

    // 2. General
    name: '',
    summary: '',
    description: '',
    logoUrlLarge: '',
    logoUrlSmall: '',
    catalogRefs: [],

    // 3. Resource Requirements
    minCpu: 0,
    recommendedCpu: 0,
    minMemory: 0,
    recommendedMemory: 0,
    minDisk: 0,
    recommendedDisk: 0,
    hpaEnabled: false,
    minReplicas: 1,
    maxReplicas: 10,
    cpuThreshold: 80,
    memoryThreshold: 80,

    // 4. Network
    ports: [],
    ingressEnabled: false,
    ingressUrl: '',
    defaultPort: 80,

    // 5. etc
    registeredById: null,
    createdAt: null,
    updatedAt: null,
  } as any
  refData.value = [{ refId: 0, refValue: '', refDesc: '', refType: 'URL' }]
  portMappings.value = [{ targetPort: 80, hostPort: 8080, protocol: 'TCP' }]
}

const addRef = () => {
  refData.value.push({ refId: 0, refValue: '', refDesc: '', refType: 'URL' })
}
const removeRef = (idx: number) => {
  if (refData.value.length > 1) refData.value.splice(idx, 1)
}

const addPortMapping = () => {
  portMappings.value.push({ targetPort: 80, hostPort: 8080, protocol: 'TCP' })
}
const removePortMapping = (idx: number) => {
  if (portMappings.value.length > 1) portMappings.value.splice(idx, 1)
}

const closeModal = () => {
  if (wizardModal.value) {
    // 모달 닫힘 플래그 미리 설정
    isModalOpen.value = false
    
    // 가장 안전한 방법: 닫기 버튼 클릭 시뮬레이션
    const closeButton = wizardModal.value.querySelector('[data-bs-dismiss="modal"]') as HTMLElement
    if (closeButton) {
      closeButton.click()
    } else {
      // 닫기 버튼이 없는 경우 직접 모달 닫기
      try {
        const bootstrap = (window as any).bootstrap
        if (bootstrap?.Modal) {
          const modal = bootstrap.Modal.getInstance(wizardModal.value) || new bootstrap.Modal(wizardModal.value)
          modal.hide()
        } else {
          // Bootstrap이 없는 경우 DOM 조작
          wizardModal.value.classList.remove('show')
          wizardModal.value.style.display = 'none'
          document.body.classList.remove('modal-open')
          
          const backdrop = document.querySelector('.modal-backdrop')
          backdrop?.remove()
        }
      } catch (error) {
        console.warn('Modal close failed:', error)
      }
    }
  }
}

const getRegistryImportKey = () => [
  catalogDto.value.target,
  getRegistryResultKey(selectedRegistryPackage.value),
  catalogDto.value.version
].join('|')

const importSelectedRegistryPackage = async () => {
  if (registrationSource.value !== 'public') return
  if (!selectedRegistryPackage.value) throw new Error('Select a package to import.')

  const importKey = getRegistryImportKey()
  if (importedRegistryKey.value === importKey) return

  registryImporting.value = true
  try {
    const source = JSON.parse(JSON.stringify(selectedRegistryPackage.value))
    let response

    if (catalogDto.value.target === 'VM') {
      const selectedCategory = catalogDto.value.category
      const payload = {
        ...source,
        tag: catalogDto.value.version,
        sourceType: 'DockerHub',
        createdAt: source.created_at,
        updatedAt: source.updated_at,
        shortDescription: source.short_description,
        starCount: source.star_count,
        ratePlans: source.rate_plans,
        categories: [{
          name: selectedCategory,
          slug: selectedCategory.toLowerCase().replace(/_/g, '-')
        }]
      }
      delete payload.created_at
      delete payload.updated_at
      delete payload.short_description
      delete payload.star_count
      delete payload.rate_plans
      response = await upLoadDockerHubApplication(payload)
    } else {
      response = await upLoadArtifactHubApplication({
        ...source,
        packageId: source.packageId,
        tag: catalogDto.value.version,
        version: catalogDto.value.version,
        category: catalogDto.value.category,
        sourceType: 'ArtifactHub'
      })
    }

    if (response?.data?.success !== true) {
      throw new Error(response?.data?.message || `${registryProviderName.value} import failed.`)
    }
    if (catalogDto.value.target === 'VM') {
      catalogDto.value.packageInfoId = response.data.packageInfoId
      if (!catalogDto.value.packageInfoId) throw new Error('Imported Docker package was not returned.')
    } else {
      catalogDto.value.helmChartId = response.data.helmChart?.id
      if (!catalogDto.value.helmChartId) throw new Error('Imported Helm package was not returned.')
    }
    importedRegistryKey.value = importKey
  } finally {
    registryImporting.value = false
  }
}

const create = async () => {
  if (isSubmitting.value) return
  isSubmitting.value = true
  try {
    await importSelectedRegistryPackage()

    // Reference 데이터 설정
    catalogDto.value.catalogRefs = refData.value.filter(ref => ref.refValue.trim())
    
    // 포트 매핑 데이터 설정 (K8S인 경우)
    if (catalogDto.value.target === 'K8S') {
      catalogDto.value.ports = portMappings.value.filter(port => port.targetPort && port.hostPort)
    }
    
    // sourceType 설정 (target에 따라)
    if (catalogDto.value.target === 'VM') {
      catalogDto.value.sourceType = 'DOCKERHUB'
    } else if (catalogDto.value.target === 'K8S') {
      catalogDto.value.sourceType = 'ARTIFACTHUB'
    }
    
    await createSoftwareCatalog(catalogDto.value)
    toast.success('Registration Success')
    
    // 모달 닫기
    closeModal()
    
    emit('created')
  } catch (e: any) {
    console.log(e)
    toast.error(e?.message || 'Registration Failed')
  } finally {
    isSubmitting.value = false
  }
}

const update = async () => {
  if (isSubmitting.value) return
  isSubmitting.value = true
  try {
    // Reference 데이터 설정
    catalogDto.value.catalogRefs = refData.value.filter(ref => ref.refValue.trim())
    
    // 포트 매핑 데이터 설정 (K8S인 경우)
    if (catalogDto.value.target === 'K8S') {
      catalogDto.value.ports = portMappings.value.filter(port => port.targetPort && port.hostPort)
    }
    
    // sourceType 설정 (target에 따라)
    if (catalogDto.value.target === 'VM') {
      catalogDto.value.sourceType = 'DOCKERHUB'
    } else if (catalogDto.value.target === 'K8S') {
      catalogDto.value.sourceType = 'ARTIFACTHUB'
    }
    
    await updateSoftwareCatalog(catalogDto.value)
    toast.success('Update Success')
    
    // 모달 닫기
    closeModal()
    
    emit('updated')
  } catch (e) {
    toast.error('Update Failed')
  } finally {
    isSubmitting.value = false
  }
}

// catalogInfo를 이용해 초기 카테고리 설정 후 데이터 로드
const loadCatalogDataWithCategoryInit = async () => {
  try {
    if (!catalogId.value) return
    
    // 내부 catalogInfo를 이용해 초기 카테고리 설정
    if (catalogInfo.value && catalogInfo.value.category) {
      // 초기화 플래그 설정
      isInitializing.value = true
      
      // target 설정 (sourceType으로부터 추론)
      catalogDto.value.target = catalogInfo.value.packageInfo !== null ? 'VM' : 'K8S'

      catalogDto.value.category = catalogInfo.value.category
      if(catalogInfo.value.packageInfo !== null) {
        catalogDto.value.packageName = catalogInfo.value.packageInfo.packageName
        catalogDto.value.version = catalogInfo.value.packageInfo.packageVersion
      } else if(catalogInfo.value.helmChart !== null) {
        catalogDto.value.packageName = catalogInfo.value.helmChart.chartName
        catalogDto.value.version = catalogInfo.value.helmChart.chartVersion
      }

      // 초기화 플래그 해제
      isInitializing.value = false
    }
    
    // 상세 데이터 로드
    await loadCatalogData()
    
  } catch (e) {
    toast.error('Failed to load catalog data')
    isInitializing.value = false
  }
}

const loadCatalogData = async () => {
  try {
    if (!catalogId.value) return
    const { data } = await getSoftwareCaltalogDetail(catalogId.value)
    
    // 초기화 플래그 설정 시작
    isInitializing.value = true
    
    // 기본 데이터 설정
    catalogDto.value = {
      ...catalogDto.value,
      ...data,
      target: data.packageInfo !== null ? 'VM' : 'K8S'
    }

    if(data.packageInfo !== null) {
      catalogDto.value.packageName = data.packageInfo.packageName
      catalogDto.value.version = data.packageInfo.packageVersion
    }
    if(data.helmChart !== null) {
      catalogDto.value.packageName = data.helmChart.chartName
      catalogDto.value.version = data.helmChart.chartVersion
    }
    
    // Reference 데이터 설정
    if (data.catalogRefs && data.catalogRefs.length > 0) {
      refData.value = data.catalogRefs.map((ref: any) => ({
        refId: ref.id || 0,
        refValue: ref.refValue || '',
        refDesc: ref.refDesc || '',
        refType: ref.refType || 'URL'
      }))
    } else {
      refData.value = [{ refId: 0, refValue: '', refDesc: '', refType: 'URL' }]
    }
    
    // 포트 매핑 데이터 설정
    if (data.ports && data.ports.length > 0) {
      portMappings.value = data.ports.map((port: any) => ({
        targetPort: port.targetPort || 80,
        hostPort: port.hostPort || 8080,
        protocol: port.protocol || 'TCP'
      }))
    } else {
      portMappings.value = [{ targetPort: 80, hostPort: 8080, protocol: 'TCP' }]
    }
    
    // 카테고리, 패키지, 버전 목록 로드
    await _getCategoryList()
    if (catalogDto.value.category) {
      await _getPackageList()
      if (catalogDto.value.packageName) {
        await _getVersionList()
      }
    }
    
    // 초기화 플래그 해제
    isInitializing.value = false
    
  } catch (e) {
    toast.error('Failed to load catalog data')
    // 오류 발생 시에도 플래그 해제
    isInitializing.value = false
  }
}

const onClickStep = (num: number) => {
  if(!isSubmitting.value && canGoNext.value) {
    currentStep.value = num
  }
}

watch(registrationSource, async () => {
  if (props.mode !== 'new' || isInitializing.value) return

  catalogDto.value.category = ''
  catalogDto.value.packageName = ''
  catalogDto.value.version = ''
  catalogDto.value.name = ''
  catalogDto.value.summary = ''
  catalogDto.value.description = ''
  catalogDto.value.logoUrlLarge = ''
  catalogDto.value.logoUrlSmall = ''
  resetRegistrySelection()

  if (registrationSource.value === 'existing') {
    await _getCategoryList()
  } else {
    catalogDto.value.sourceType = catalogDto.value.target === 'VM' ? 'DOCKERHUB' : 'ARTIFACTHUB'
    await loadPublicCategories()
  }
})

// target 값 변경 시 패키지 소스에 맞는 목록 초기화
watch(() => catalogDto.value.target, async (newTarget, oldTarget) => {
  if (!newTarget || isInitializing.value || newTarget === oldTarget) return

  if (props.mode === 'new') {
    catalogDto.value.category = ''
    catalogDto.value.packageName = ''
    catalogDto.value.version = ''
  }

  if (usesExistingPackage.value) {
    await _getCategoryList()
  } else {
    catalogDto.value.sourceType = newTarget === 'VM' ? 'DOCKERHUB' : 'ARTIFACTHUB'
    resetRegistrySelection()
    await loadPublicCategories()
  }
})

const categoryList = ref([] as { key: string, value: string }[])
const _getCategoryList = async () => {
  
  // 초기화 중이 아닐 때만 필드들을 초기화
  if (props.mode === 'new' && usesExistingPackage.value) {
    categoryList.value = []
    packageList.value = []
    versionList.value = [] 
    catalogDto.value.category = ''
    catalogDto.value.packageName = ''
    catalogDto.value.version = ''
  }

  if(catalogDto.value.target) {
    const param = {
      target: catalogDto.value.target === 'VM' ? 'DOCKER' : 'HELM',
      availableOnly: props.mode === 'new'
    }
    const { data } = await getCategoryList(param)
    categoryList.value = data
  }
}

// category 값 변경 시 패키지 목록 조회
watch(() => catalogDto.value.category, (category) => {
  if (category && usesExistingPackage.value && !isInitializing.value) {
    // 초기화 중이 아닐 때만 하위 필드들을 초기화
    if (props.mode === 'new') {
      catalogDto.value.packageName = ''
      catalogDto.value.version = ''
    }
    _getPackageList()
  }
})

const packageList = ref([] as { key: string, value: string }[])
const _getPackageList = async () => {
  
  // 초기화 중이 아닐 때만 필드들을 초기화
  if (props.mode === 'new' && usesExistingPackage.value) {
    packageList.value = []
    versionList.value = []
    catalogDto.value.packageName = ''
    catalogDto.value.version = ''
  }

  const param = {
    target: catalogDto.value.target === 'VM' ? 'DOCKER' : 'HELM',
    category: catalogDto.value.category || '',
    availableOnly: props.mode === 'new'
  }
  const { data } = await getPackageList(param)
  packageList.value = data
}

watch(() => catalogDto.value.packageName, (packageName) => {
  if (packageName && usesExistingPackage.value && !isInitializing.value) {
    // 초기화 중이 아닐 때만 version을 초기화
    if (props.mode === 'new') {
      catalogDto.value.version = ''
    }
    _getVersionList()
  }
})

const versionList = ref([] as any[])
const _getVersionList = async () => {
  // 초기화 중이 아닐 때만 version을 초기화
  if (props.mode === 'new' && usesExistingPackage.value) {
    versionList.value = []
    catalogDto.value.version = ''
  }

  const param = {
    target: catalogDto.value.target === 'VM' ? 'DOCKER' : 'HELM',
    packageName: catalogDto.value.packageName || '',
    availableOnly: props.mode === 'new'
  }
  const { data } = await getVersionList(param)

  if(props.mode === 'new') {
    data.forEach((item: any) => {
      if(!item.isUsed) {
        versionList.value.push(item) 
      }
    })
  }
  else {
    versionList.value = data
  }
}

// 새로운 카탈로그 생성을 위한 초기화
const initForCreate = () => {
  reset()
  currentStep.value = 1
}

// 카탈로그 업데이트를 위한 데이터 설정
const initForUpdate = (id: number, info: any) => {
  catalogId.value = id
  catalogInfo.value = info
  currentStep.value = 1
  loadCatalogDataWithCategoryInit()
}

// 부모 컴포넌트에서 접근 가능하도록 메서드 노출
defineExpose({
  loadCatalogDataWithCategoryInit,
  initForCreate,
  initForUpdate
})

</script>

<style scoped>
.nav-link {
  cursor: default;
}

.source-option {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  min-height: 92px;
  padding: 1rem;
  border: 1px solid var(--tblr-border-color);
  border-radius: var(--tblr-border-radius);
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.source-option.selected {
  border-color: var(--tblr-primary);
  box-shadow: 0 0 0 1px var(--tblr-primary);
}

.source-option small {
  display: block;
  margin-top: 0.25rem;
  color: var(--tblr-secondary-color);
}

.registry-results {
  max-height: 280px;
  overflow-y: auto;
}

.registry-logo {
  flex: 0 0 auto;
  object-fit: contain;
  background: #fff;
}

.registry-description,
.registry-results .list-group-item.active .registry-description {
  color: var(--tblr-secondary-color) !important;
}
</style>
