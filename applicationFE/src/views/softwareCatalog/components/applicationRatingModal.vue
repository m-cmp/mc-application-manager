<template>
  <div class="modal modal-blur fade" id="rating-modal" tabindex="-1" role="dialog" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Application Rating</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <form @submit.prevent="submitRating">
            <!-- Overall Rating -->
            <div class="mb-3">
              <label class="form-label">Overall Rating</label>
              <div class="rating-stars">
                <span 
                  v-for="star in 5" 
                  :key="star"
                  class="star"
                  :class="{ active: star <= ratingForm.rating }"
                  @click="setRating(star)"
                >
                  ★
                </span>
              </div>
            </div>

            <!-- Category -->
            <div class="mb-3">
              <label class="form-label">Category</label>
              <select v-model="ratingForm.category" class="form-select" required>
                <option value="">Select Category</option>
                <option value="Performance">Performance</option>
                <option value="Usability">Usability</option>
                <option value="Reliability">Reliability</option>
                <option value="Security">Security</option>
                <option value="Support">Support</option>
              </select>
            </div>

            <!-- Detailed Comments -->
            <div class="mb-3">
              <label class="form-label">Detailed Comments</label>
              <textarea 
                v-model="ratingForm.detailedComments"
                class="form-control" 
                rows="4"
                placeholder="Enter detailed comments"
                required
              ></textarea>
            </div>

            <!-- Name -->
            <div class="mb-3">
              <label class="form-label">Name</label>
              <input 
                v-model="ratingForm.name"
                type="text" 
                class="form-control"
                placeholder="Enter your name"
                required
              >
            </div>

            <!-- Email -->
            <div class="mb-3">
              <label class="form-label">Email</label>
              <input 
                v-model="ratingForm.email"
                type="email" 
                class="form-control"
                placeholder="Enter your email"
                required
              >
            </div>
          </form>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
          <button type="button" class="btn btn-primary" @click="submitRating" :disabled="!isFormValid">Submit</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useToast } from 'vue-toastification'
import { submitApplicationRating } from '@/api/softwareCatalog'

interface Props {
  catalogId: number
  applicationName: string
}

const props = defineProps<Props>()
const emit = defineEmits(['ratingSubmitted'])

const toast = useToast()

const ratingForm = ref({
  rating: 0,
  category: '',
  detailedComments: '',
  name: '',
  email: ''
})

const setRating = (rating: number) => {
  ratingForm.value.rating = rating
}

const isFormValid = computed(() => {
  return ratingForm.value.rating > 0 &&
         ratingForm.value.category &&
         ratingForm.value.detailedComments &&
         ratingForm.value.name &&
         ratingForm.value.email
})

const resetForm = () => {
  ratingForm.value = {
    rating: 0,
    category: '',
    detailedComments: '',
    name: '',
    email: ''
  }
}

const closeModal = () => {
  const modal = document.getElementById('rating-modal')
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
}

const submitRating = async () => {
  if (!isFormValid.value) {
    toast.error('Please fill in all fields')
    return
  }

  try {
    const payload = {
      catalogId: props.catalogId,
      rating: ratingForm.value.rating,
      category: ratingForm.value.category,
      detailedComments: ratingForm.value.detailedComments,
      name: ratingForm.value.name,
      email: ratingForm.value.email,
      metadata: JSON.stringify({
        version: "1.0.0",
        environment: "production",
        applicationName: props.applicationName
      })
    }

    const response = await submitApplicationRating(payload)
    
    // API 호출이 성공적으로 완료된 경우에만 실행
    if (response) {
      toast.success('Rating submitted successfully')
      resetForm()
      emit('ratingSubmitted')
      
      // 모달 닫기
      closeModal()
    }
    
  } catch (error) {
    console.error('Rating submission error:', error)
    toast.error('Failed to submit rating')
  }
}
</script>

<style scoped>
.rating-stars {
  display: flex;
  gap: 5px;
  margin-bottom: 10px;
}

.star {
  font-size: 24px;
  color: #ddd;
  cursor: pointer;
  transition: color 0.2s;
}

.star:hover,
.star.active {
  color: #ffc107;
}

.star:hover ~ .star {
  color: #ddd;
}
</style>
