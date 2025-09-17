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
import { ref, watch } from 'vue'
import { IconUpload } from '@tabler/icons-vue'
import { useToast } from 'vue-toastification'
import { getApplicationTag } from '@/api/softwareCatalog'
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
  tag: ''
})

// Props 변경 감지하여 폼 데이터 업데이트
watch(() => [props.sourceData?.sourceType, props.sourceData?.name], () => {
  console.log(props.sourceData)
  if (props.sourceData?.sourceType) {
    formData.value.sourceType = props.sourceData?.sourceType
  }
  if (props.sourceData?.name) {
    formData.value.name = props.sourceData?.name
    _getApplicationTag(props.sourceData?.name)
  }
}, { immediate: true })

const tagList = ref([] as any)
const _getApplicationTag = async (name: string) => {
  const params = {
    path: props.sourceData?.id || ''
  }

  const { data } = await getApplicationTag(params)
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

const onSubmit = () => {
  if (!formData.value.tag.trim()) {
    toast.error('Tag is required.')
    return
  }

  const emitData = _.cloneDeep(props.sourceData)
  emitData.tag = formData.value.tag
  emitData.sourceType = formData.value.sourceType
  emitData.name = formData.value.name

  // 업로드 데이터 emit
  emits('uploaded', emitData)

  // 폼 초기화
  formData.value.tag = ''
  
  // 모달 닫기
  hide()
}

// 백드롭 클릭 시 모달 닫기
const onBackdropClick = () => {
  hide()
}

// 외부에서 모달 열기 위한 메서드
const show = () => {
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