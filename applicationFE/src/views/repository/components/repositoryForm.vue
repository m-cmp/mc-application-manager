<template>
  <div class="modal fade" id="repositoryForm" tabindex="-1" ref="modalElement">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">

        <div class="modal-header">
          <h3 class="modal-title">Repository {{ props.mode === 'new' ? 'Create' : 'Update'}}</h3>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>

        <div class="modal-body py-4">
          <div>
            <!-- Repository 명 -->
            <div class="row mb-3">
              <label class="form-label required">Name</label>
              <input type="text" class="form-control p-2 g-col-11" v-model="repositoryFormData.name" :disabled="props.mode != 'new'" placeholder="Enter repository name" />
            </div>
            
            <!-- Format -->
            <div class="mb-3">
              <label class="form-label required">Format</label>
              <div>
                <!-- <label class="form-check form-check-inline">
                  <input class="form-check-input" type="radio" name="format" value="raw" v-model="repositoryFormData.format" :disabled="props.mode != 'new'" />
                  <span class="form-check-label">raw</span>
                </label> -->
                <label class="form-check form-check-inline">
                  <input class="form-check-input" type="radio" name="format" value="helm" v-model="repositoryFormData.format" :disabled="props.mode != 'new'" />
                  <span class="form-check-label">helm</span>
                </label>
                <label class="form-check form-check-inline">
                  <input class="form-check-input" type="radio" name="format" value="docker" v-model="repositoryFormData.format" :disabled="props.mode != 'new'" />
                  <span class="form-check-label">docker</span>
                </label>
              </div>
            </div>

            <!-- Allow -->
            <div class="mb-3">
              <label class="form-label required">Allow</label>
              <div>
                <label class="form-check form-check-inline">
                  <input class="form-check-input" type="radio" name="allow" value="allow" v-model="writePolicy">
                  <span class="form-check-label">allow</span>
                </label>
                <label class="form-check form-check-inline">
                  <input class="form-check-input" type="radio" name="allow" value="allow_once" v-model="writePolicy">
                  <span class="form-check-label">allow_once</span>
                </label>
                <label class="form-check form-check-inline">
                  <input class="form-check-input" type="radio" name="allow" value="deny" v-model="writePolicy">
                  <span class="form-check-label">deny</span>
                </label>
              </div>
            </div>

            <!-- On/Offline -->
            <div class="mb-3">
              <label class="form-label required">On/Offline</label>
              <div>
                <label class="form-check form-check-inline">
                  <input class="form-check-input" type="radio" name="online" value="true" v-model="repositoryFormData.online">
                  <span class="form-check-label">true</span>
                </label>
                <label class="form-check form-check-inline">
                  <input class="form-check-input" type="radio" name="online" value="false" v-model="repositoryFormData.online">
                  <span class="form-check-label">false</span>
                </label>
              </div>
            </div>

            <!-- <div class="mb-3">
              <label class="form-label required">Storage</label>
              <div class="grid gap-0 column-gap-3">
                <input type="text" class="form-control p-2 g-col-11" value="default" disabled />
              </div>
            </div>

            <div class="mb-3">
              <label class="form-label required">Http</label>
              <div class="grid gap-0 column-gap-3">
                <input type="number" class="form-control p-2 g-col-11" v-model="httpPort" :disabled="repositoryFormData.format != 'docker'" placeholder="Enter HTTP port" />
              </div>
            </div>

            <div class="mb-3">
              <label class="form-label required">Https</label>
              <div class="grid gap-0 column-gap-3">
                <input type="number" class="form-control p-2 g-col-11" v-model="httpsPort" :disabled="repositoryFormData.format != 'docker'" placeholder="Enter HTTPS port" />
              </div>
            </div> -->

          </div>
        </div>

      <div class="modal-footer">
        <button type="button" class="btn btn-link link-secondary" data-bs-dismiss="modal" @click="setInit()">
          Cancel
        </button>
        <button type="button" ref="submitBtn" class="btn btn-primary ms-auto" @click="onClickSubmit()">
          {{props.mode === 'new' ? 'Create' : 'Update'}}
        </button>
      </div>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// @ts-ignore
import type { Repository } from '../../type/type';
import { ref, onMounted as vueOnMounted } from 'vue';
import { useToast } from 'vue-toastification';
// @ts-ignore
import { registRepository, getRepositoryDetailInfo, updateRepository } from '@/api/repository';
import { onMounted } from 'vue';
import { computed } from 'vue';
import { watch } from 'vue';
import { Modal } from 'bootstrap'

const toast = useToast()

/**
 * @Title Modal 관리
 */
const modalElement = ref<HTMLElement>()
const modalInstance = ref<Modal>()

/**
 * @Title Props / Emit
 */
interface Props {
  mode: String
  repositoryName: string
}
const props = defineProps<Props>()
const emit = defineEmits(['get-repository-list'])
const repositoryName = computed(() => props.repositoryName);
watch(repositoryName, async () => {
  await setInit();
});

onMounted(async () => {
  // Modal 인스턴스 초기화
  if (modalElement.value) {
    modalInstance.value = new Modal(modalElement.value)
  }
  
  await setInit();
})

const repositoryFormData = ref({} as Repository)
const writePolicy = ref("" as string)
const httpPort = ref(0 as number)
const httpsPort = ref(0 as number)

const setInit = async () => {
  if (props.mode === 'new') {
    repositoryFormData.value.name = ''
    repositoryFormData.value.format = 'helm'
    repositoryFormData.value.type = 'hosted'
    repositoryFormData.value.url = ''
    repositoryFormData.value.online = true
    httpPort.value = 0
    httpsPort.value = 0
    writePolicy.value = "allow"
  }
  else {
    const { data } = await getRepositoryDetailInfo("nexus", props.repositoryName)
    repositoryFormData.value = data
    writePolicy.value = data.storage.writePolicy
    if(data.format == "docker") {
      httpPort.value = data.docker.httpPort
      httpsPort.value = data.docker.httpsPort
    }
  }
}

/**
 * @Title onClickSubmit
 * @Desc 
 *     1. 생성 / 수정 버튼 클릭시 동작
 *     2. 부모로 부터 받은 mode값에 따라서 생성/수정 Callback 함수 호출후 부모에게 repository 목록 api 호출  
 */
const onClickSubmit = async () => {
  // ================= Validation ==================
  if (!repositoryFormData.value.name || repositoryFormData.value.name.trim() === '') {
    toast.error('Please enter repository name.');
    return;
  }
  
  if (!repositoryFormData.value.format) {
    toast.error('Please select format.');
    return;
  }
  
  if (!writePolicy.value) {
    toast.error('Please select allow policy.');
    return;
  }
  
  if (repositoryFormData.value.online === undefined || repositoryFormData.value.online === null) {
    toast.error('Please select online/offline status.');
    return;
  }
  
  // Docker format인 경우 포트 검증
  if (repositoryFormData.value.format === 'docker') {
    if (!httpPort.value || httpPort.value <= 0) {
      toast.error('Please enter valid HTTP port.');
      return;
    }
    
    if (!httpsPort.value || httpsPort.value <= 0) {
      toast.error('Please enter valid HTTPS port.');
      return;
    }
  }

  repositoryFormData.value.storage = {
    "blobStoreName": "default",
    "strictContentTypeValidation": true,
    "writePolicy": writePolicy.value
  }

  if(repositoryFormData.value.format != "docker") {
    repositoryFormData.value.docker = {}
  } else {
    repositoryFormData.value.docker = {
      "v1Enabled": true,
      "forceBasicAuth": true,
      "httpPort": httpPort.value,
      "httpsPort": httpsPort.value,
      "subdomain": "/test"
    }
  }

  let success = false;
  
  if (props.mode === 'new') {
    success = await _registRepository();
  } else {
    success = await _updateRepository();
  }
  
  // 성공적으로 처리된 경우에만 모달 닫기
  if (success) {
    emit('get-repository-list');
    setInit();
    
    // 모달 닫기
    if (modalInstance.value) {
      modalInstance.value.hide()
      // 백드롭이 남아있을 경우 강제 제거
      setTimeout(() => {
        document.body.classList.remove('modal-open')
        const backdrop = document.querySelector('.modal-backdrop')
        backdrop?.remove()
      }, 150)
    }
  }
}

/**
 * @Title _registRepository
 * @Desc 생성 Callback 함수 / 생성 api 호출
 */
const _registRepository = async (): Promise<boolean> => {
  try {
    const { data } = await registRepository("nexus", repositoryFormData.value)
    if (data) {
      toast.success('Repository created successfully.')
      return true
    } else {
      toast.error('Failed to create repository.')
      return false
    }
  } catch (error) {
    toast.error('Failed to create repository.')
    return false
  }
}

/**
 * @Title _updateRepository
 * @Desc 수정 Callback 함수 / 수정 api 호출
 */
const _updateRepository = async (): Promise<boolean> => {
  try {
    const { data } = await updateRepository("nexus", repositoryFormData.value)
    if (data) {
      toast.success('Repository updated successfully.')
      return true
    } else {
      toast.error('Failed to update repository.')
      return false
    }
  } catch (error) {
    toast.error('Failed to update repository.')
    return false
  }
}

</script>