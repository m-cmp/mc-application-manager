<template>
  <div 
    class="modal modal-blur fade" 
    id="upload-form-modal" 
    tabindex="-1" 
    role="dialog" 
    aria-hidden="true"
    @click="onBackdropClick">
    <div class="modal-dialog modal-lg modal-dialog-centered" role="document" @click.stop>
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Upload Application</h5>
          <button type="button" class="btn-close" @click="hide" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <form @submit.prevent="onSubmit">
            <div class="row">
              <div class="col-lg-12">
                <div class="mb-3">
                  <label class="form-label">Source Type</label>
                  <input 
                    type="text" 
                    class="form-control" 
                    v-model="formData.sourceType"
                    disabled>
                </div>
              </div>
            </div>
            
            <div class="row">
              <div class="col-lg-12">
                <div class="mb-3">
                  <label class="form-label">Name</label>
                  <input 
                    type="text" 
                    class="form-control" 
                    v-model="formData.name"
                    disabled>
                </div>
              </div>
            </div>
            
            <div class="row">
              <div class="col-lg-12">
                <div class="mb-3">
                  <label class="form-label">Tag <span class="text-red">*</span></label>
                  <select class="form-select" v-model="formData.tag">
                    <option value="">Select Tag</option>
                    <option v-for="tag in tagList" :value="tag.value" :key="tag.key">{{ tag.value }}</option>
                  </select>
                  <small class="form-hint">Please enter the tag for this catalog.</small>
                </div>
              </div>
            </div>

            <div class="row" v-if="isArtifactHub">
              <div class="col-lg-12">
                <div class="mb-3">
                  <label class="form-label">Category <span class="text-red">*</span></label>
                  <select class="form-select" v-model="formData.category">
                    <option value="">Select Category</option>
                    <option v-for="category in categoryList" :value="category.value" :key="category.key">{{ category.value }}</option>
                    <option :value="CUSTOM_CATEGORY_VALUE">Custom...</option>
                  </select>
                </div>
              </div>
            </div>

            <div class="row" v-if="isArtifactHub && formData.category === CUSTOM_CATEGORY_VALUE">
              <div class="col-lg-12">
                <div class="mb-3">
                  <label class="form-label">Custom Category <span class="text-red">*</span></label>
                  <input
                    type="text"
                    class="form-control"
                    v-model="formData.customCategory"
                    placeholder="Enter category">
                </div>
              </div>
            </div>
          </form>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-link link-secondary" @click="hide">
            Cancel
          </button>
          <button type="submit" class="btn btn-primary ms-auto" @click="onSubmit">
            <IconUpload class="icon" />
            Upload
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import { IconUpload } from '@tabler/icons-vue'
import { useToast } from 'vue-toastification'
import { getApplicationTagForDockerHub, getApplicationTagForArtifactHub, getCategoryList } from '@/api/softwareCatalog'
// @ts-ignore
import _ from 'lodash';

const toast = useToast()

interface Props {
  sourceData?: any
}

const props = defineProps<Props>()

const emits = defineEmits<{
  uploaded: [data: any]
  close: []
}>()

const formData = ref<any>({
  path: '',
  sourceType: '',
  name: '',
  tag: '',
  category: '',
  customCategory: ''
})

const CUSTOM_CATEGORY_VALUE = '__CUSTOM__'
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

// Props 변경 감지하여 폼 데이터 업데이트
watch(() => [props.sourceData?.sourceType, props.sourceData?.name], async () => {
  if (props.sourceData?.sourceType) {
    formData.value.sourceType = props.sourceData?.sourceType
  }
  if (props.sourceData?.name) {
    formData.value.name = props.sourceData?.name
    console.log(formData.value.sourceType)
    if(formData.value.sourceType.toUpperCase() == 'DOCKERHUB') {
      _getApplicationTagForDockerHub(props.sourceData?.name)
    } else if(formData.value.sourceType.toUpperCase() == 'ARTIFACTHUB') {
      _getApplicationTagForArtifactHub(props.sourceData)
      await _getCategoryListForArtifactHub()
      applyRecommendedCategory(props.sourceData)
    }
  }
}, { immediate: true })

const tagList = ref([] as any)
const categoryList = ref([] as { key: string, value: string }[])
const isArtifactHub = computed(() => formData.value.sourceType?.toUpperCase() === 'ARTIFACTHUB')

onUnmounted(() => {
  resetFormData()
})

// 폼 데이터 초기화 함수
const resetFormData = () => {
  formData.value = {
    path: '',
    sourceType: '',
    name: '',
    tag: '',
    category: '',
    customCategory: ''
  }
  tagList.value = []
  categoryList.value = []
}

const _getApplicationTagForDockerHub = async (sourceData: any) => {
  const params = {
    path: props.sourceData?.id || ''
  }

  const { data } = await getApplicationTagForDockerHub(params)
  tagList.value = []
  if(data.length > 0) {
    data.forEach((item: any) => {
      tagList.value.push({
        key: item.name,
        value: item.name
      })
    })
  }
}

const _getApplicationTagForArtifactHub = async (sourceData: any) => {
  console.log("sourceData",sourceData)

  const params = {
    kind: 'helm',
    repository: sourceData.repository.name,
    packageName: sourceData.name
  }
  const { data } = await getApplicationTagForArtifactHub(params)
  tagList.value = []
  if(data.length > 0) {
    data.forEach((item: any) => {
      tagList.value.push({
        key: item.version,
        value: item.version
      })
    })
  }
}

const _getCategoryListForArtifactHub = async () => {
  const categoryMap = new Map<string, { key: string, value: string }>()
  const addCategory = (value: any) => {
    const normalizedValue = normalizeCategoryValue(value)
    if (!normalizedValue) return
    categoryMap.set(normalizedValue, {
      key: normalizedValue,
      value: normalizedValue
    })
  }

  DEFAULT_CATEGORY_OPTIONS.forEach(addCategory)

  try {
    const [{ data: helmCategories }, { data: dockerCategories }] = await Promise.all([
      getCategoryList({ target: 'HELM', availableOnly: false }),
      getCategoryList({ target: 'DOCKER', availableOnly: false })
    ])

    ;[...(helmCategories || []), ...(dockerCategories || [])].forEach((category: any) => {
      addCategory(category?.value || category?.key || category)
    })
  } catch (error) {
    console.log(error)
  }

  categoryList.value = Array.from(categoryMap.values()).sort((a, b) => a.value.localeCompare(b.value))
}

const applyRecommendedCategory = (sourceData: any) => {
  const recommendedCategory = normalizeCategoryValue(
    sourceData?.category ||
    sourceData?.categories?.[0] ||
    sourceData?.categoryName ||
    sourceData?.category_name
  )

  if (!recommendedCategory) {
    formData.value.category = ''
    formData.value.customCategory = ''
    return
  }

  const hasCategoryOption = categoryList.value.some((category) => category.value === recommendedCategory)
  if (hasCategoryOption) {
    formData.value.category = recommendedCategory
    formData.value.customCategory = ''
    return
  }

  formData.value.category = CUSTOM_CATEGORY_VALUE
  formData.value.customCategory = recommendedCategory
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

  if (CATEGORY_ID_MAP[rawValue]) {
    return CATEGORY_ID_MAP[rawValue]
  }

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

const getSelectedCategory = () => {
  if (!isArtifactHub.value) return ''
  if (formData.value.category === CUSTOM_CATEGORY_VALUE) {
    return normalizeCategoryValue(formData.value.customCategory)
  }
  return normalizeCategoryValue(formData.value.category)
}

const onSubmit = () => {
  if (!formData.value.tag.trim()) {
    toast.error('Tag is required.')
    return
  }

  const selectedCategory = getSelectedCategory()
  if (isArtifactHub.value && !selectedCategory) {
    toast.error('Category is required.')
    return
  }

  const emitData = _.cloneDeep(props.sourceData)
  emitData.tag = formData.value.tag
  emitData.sourceType = formData.value.sourceType
  emitData.name = formData.value.name
  if (isArtifactHub.value) {
    emitData.version = formData.value.tag
    emitData.category = selectedCategory
  }

  // 업로드 데이터 emit
  emits('uploaded', emitData)

  // 폼 완전 초기화
  resetFormData()
  
  // 모달 닫기
  hide()
}

// 백드롭 클릭 시 모달 닫기
const onBackdropClick = () => {
  hide()
}

// 외부에서 모달 열기 위한 메서드
const show = () => {
  // 모달 열기 전 폼 초기화
  resetFormData()
  
  const modalElement = document.getElementById('upload-form-modal')
  if (modalElement) {
    try {
      // Bootstrap이 있는지 확인
      const bootstrap = (window as any).bootstrap
      if (bootstrap && bootstrap.Modal) {
        const modal = new bootstrap.Modal(modalElement)
        modal.show()
      } else {
        fallbackShow()
      }
    } catch (error) {
      console.warn('Failed to show modal with Bootstrap, using fallback:', error)
      fallbackShow()
    }
  }
}

// 백업 모달 열기 방법
const fallbackShow = () => {
  const modalElement = document.getElementById('upload-form-modal')
  if (modalElement) {
    // 기존 백드롭 제거
    const existingBackdrops = document.querySelectorAll('.modal-backdrop')
    existingBackdrops.forEach(backdrop => backdrop.remove())
    
    // 모달 표시
    modalElement.classList.add('show')
    modalElement.style.display = 'block'
    modalElement.style.opacity = '1'
    modalElement.setAttribute('aria-hidden', 'false')
    
    // body 스타일 설정
    document.body.classList.add('modal-open')
    
    // 백드롭 추가
    const backdrop = document.createElement('div')
    backdrop.className = 'modal-backdrop fade show'
    backdrop.id = 'upload-modal-backdrop'
    document.body.appendChild(backdrop)
  }
}

// 외부에서 모달 닫기 위한 메서드
const hide = () => {
  const modalElement = document.getElementById('upload-form-modal')
  if (modalElement) {
    try {
      const bootstrap = (window as any).bootstrap
      if (bootstrap && bootstrap.Modal) {
        const modal = bootstrap.Modal.getInstance(modalElement)
        if (modal) {
          modal.hide()
        } else {
          fallbackHide()
        }
      } else {
        fallbackHide()
      }
    } catch (error) {
      console.warn('Failed to hide modal with Bootstrap, using fallback:', error)
      fallbackHide()
    }
  }
}

// 백업 모달 닫기 방법
const fallbackHide = () => {
  const modalElement = document.getElementById('upload-form-modal')
  if (modalElement) {
    // 모든 가능한 클래스 제거
    modalElement.classList.remove('show', 'fade', 'in')
    modalElement.style.display = 'none'
    modalElement.style.opacity = '0'
    modalElement.setAttribute('aria-hidden', 'true')
    
    // body 클래스 및 스타일 복원
    document.body.classList.remove('modal-open')
    document.body.style.overflow = ''
    document.body.style.paddingRight = ''
    
    // 백드롭 제거 (여러 가능한 백드롭 모두 제거)
    const backdrops = document.querySelectorAll('.modal-backdrop, #upload-modal-backdrop')
    backdrops.forEach(backdrop => backdrop.remove())
    
    // close 이벤트 발생
    emits('close')
  }
}

// 컴포넌트 언마운트 시 초기화
onUnmounted(() => {
  resetFormData()
})

defineExpose({
  show,
  hide
})
</script>

<style scoped>
.modal-body {
  padding: 1.5rem;
}

.form-label {
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.text-red {
  color: #d63384;
}

.form-hint {
  color: #6c757d;
  font-size: 0.875rem;
}
</style>
