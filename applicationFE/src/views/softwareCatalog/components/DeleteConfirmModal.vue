<template>
  <!-- Delete Confirmation Modal -->
  <div class="modal fade" id="deleteConfirmModal" tabindex="-1" ref="deleteModal" @click.self="hide">
    <div class="modal-dialog" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Confirm Catalog Deletion</h5>
          <button type="button" class="btn-close" @click="hide" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <p>Are you sure you want to delete <strong>{{ targetCatalog.name }}</strong> ({{ targetCatalog.category }}) catalog?</p>
          <p class="text-muted">This action cannot be undone.</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="hide">Cancel</button>
          <button type="button" class="btn btn-danger" @click="confirmDelete" :disabled="isDeleting">
            <span v-if="isDeleting" class="spinner-border spinner-border-sm me-2" role="status"></span>
            {{ isDeleting ? 'Deleting...' : 'Delete' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useToast } from 'vue-toastification'
import { deleteSoftwareCatalog } from '../../../api/softwareCatalog'

interface Props {
  targetCatalog?: any
}

const props = defineProps<Props>()
const emit = defineEmits(['deleted', 'close'])

const toast = useToast()
const isDeleting = ref(false)
const deleteModal = ref<HTMLElement | null>(null)

// 모달 열기
const show = () => {
  if (deleteModal.value) {
    try {
      // Bootstrap이 있는지 확인
      const bootstrap = (window as any).bootstrap
      if (bootstrap && bootstrap.Modal) {
        const modal = new bootstrap.Modal(deleteModal.value)
        modal.show()
      } else {
        // Bootstrap이 없는 경우 직접 클래스 조작
        deleteModal.value.classList.add('show')
        deleteModal.value.style.display = 'block'
        document.body.classList.add('modal-open')
        
        // 백드롭 추가
        const backdrop = document.createElement('div')
        backdrop.className = 'modal-backdrop fade show'
        backdrop.id = 'delete-modal-backdrop'
        document.body.appendChild(backdrop)
      }
    } catch (error) {
      console.warn('Failed to show modal with Bootstrap, using fallback:', error)
      // 백업 방법
      deleteModal.value.classList.add('show')
      deleteModal.value.style.display = 'block'
      document.body.classList.add('modal-open')
    }
  }
}

// 모달 닫기
const hide = () => {
  if (deleteModal.value) {
    try {
      const bootstrap = (window as any).bootstrap
      if (bootstrap && bootstrap.Modal) {
        const modal = bootstrap.Modal.getInstance(deleteModal.value)
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
  if (deleteModal.value) {
    deleteModal.value.classList.remove('show')
    deleteModal.value.style.display = 'none'
    document.body.classList.remove('modal-open')
    
    // 백드롭 제거
    const backdrop = document.getElementById('delete-modal-backdrop')
    if (backdrop) {
      backdrop.remove()
    }
    
    // close 이벤트 발생
    emit('close')
  }
}

// 삭제 확인
const confirmDelete = async () => {
  if (!props.targetCatalog?.id) return
  
  isDeleting.value = true
  
  try {
    await deleteSoftwareCatalog(props.targetCatalog.id)
    toast.success(`${props.targetCatalog.name} catalog has been successfully deleted.`)
    
    // 모달 닫기
    hide()
    
    // 부모에게 삭제 완료 이벤트 전달
    emit('deleted', props.targetCatalog.id)
    
  } catch (error) {
    console.error('Delete failed:', error)
    toast.error('Failed to delete catalog.')
  } finally {
    isDeleting.value = false
  }
}

// 모달 이벤트 핸들러
const handleModalHide = () => {
  emit('close')
}

onMounted(() => {
  if (deleteModal.value) {
    deleteModal.value.addEventListener('hidden.bs.modal', handleModalHide)
  }
})

onBeforeUnmount(() => {
  if (deleteModal.value) {
    deleteModal.value.removeEventListener('hidden.bs.modal', handleModalHide)
  }
})

// 부모에서 사용할 수 있도록 메서드 노출
defineExpose({
  show,
  hide
})
</script>
