<template>
  <div class="modal" id="action-confirm" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">
            {{ applicationName }} {{ modalTitle }} 
            <span v-if="type">({{ type }})</span>
          </h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close" @click="setInit"></button>
        </div>
        <div class="modal-body" style="max-height: calc(100vh - 200px);overflow-y: auto;">

            <div class="mb-3">
              <label class="form-label">Reason</label>
              <p class="text-muted">
                Please enter a reason
              </p>
              <textarea class="form-control" rows="10" placeholder="Reason"  v-model="reason" />
            </div>

        </div>

        <!-- Footer -->
        <div 
          class="modal-footer d-flex justify-content-between">
          <a 
            class="btn btn-link link-secondary" 
            data-bs-dismiss="modal" 
            @click="setInit">
            Cancel
          </a>

          <div>
            <button 
              class="btn btn-primary ms-auto" 
              data-bs-dismiss="modal" 
              @click="onClickAction" >
              {{ modalTitle }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useToast } from 'vue-toastification';
import { computed } from 'vue';
import { runK8SAction, runVmAction } from '@/api/softwareCatalog';

const toast = useToast()

interface Props {
  title: string
  applicationStatusId: number
  type: string
  applicationName: string
}
const props = defineProps<Props>()
const emit = defineEmits(['getApplicationsStatusList'])


const modalTitle = computed(() => props.title);
const applicationStatusId = computed(() => props.applicationStatusId)
const type = computed(()=> props.type)
const reason = ref('' as string)

const setInit = () => {
  reason.value = ''
}

const onClickAction = async () => {
  setInit()

  let result
  const params = {
    operation: modalTitle.value,
    applicationStatusId: applicationStatusId.value,
    reason: reason.value
  }
  if (type.value === 'VM') {
    const { data } = await runVmAction(params)
    result = data
  }
  else if (type.value === 'K8S') {
    const { data } = await runK8SAction(params)
    result = data
  }

  
  emit('getApplicationsStatusList')

  if (result) {
    toast.success(`${modalTitle.value} Action SUCCESS`)
  }
  else {
    toast.error(`${modalTitle.value} Action FAIL`)
  }
}

</script>