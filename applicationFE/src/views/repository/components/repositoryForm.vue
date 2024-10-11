<template>
  <div class="modal" id="repositoryForm" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">

        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        <div class="modal-body text-left py-4">
          <!-- Repository Title -->
          <h3 class="mb-5">
            Repository {{ props.mode === 'new' ? '생성' : '수정'}}
          </h3>

          <div>
            <!-- Repository 명 -->
            <div class="row mb-3">
              <label class="form-label required">Name</label>
              <div class="grid gap-0 column-gap-3">
                <input type="text" class="form-control p-2 g-col-11" v-model="repositoryFormData.name" :disabled="props.mode != 'new'" />
              </div>
            </div>
            
            <!-- Format -->
            <div class="mb-3">
              <label class="form-label required">Format</label>
              <div>
                <label class="form-check form-check-inline">
                  <input class="form-check-input" type="radio" name="format" value="raw" v-model="repositoryFormData.format" :disabled="props.mode != 'new'" />
                  <span class="form-check-label">raw</span>
                </label>
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

            <div class="mb-3">
              <label class="form-label required">Storage</label>
              <div class="grid gap-0 column-gap-3">
                <input type="text" class="form-control p-2 g-col-11" value="defalut" disabled />
              </div>
            </div>

            <div class="mb-3">
              <label class="form-label required">Http</label>
              <div class="grid gap-0 column-gap-3">
                <input type="text" class="form-control p-2 g-col-11" v-model="httpPort" :disabled="repositoryFormData.format != 'docker'" />
              </div>
            </div>

            <div class="mb-3">
              <label class="form-label required">Https</label>
              <div class="grid gap-0 column-gap-3">
                <input type="text" class="form-control p-2 g-col-11" v-model="httpsPort" :disabled="repositoryFormData.format != 'docker'" />
              </div>
            </div>

          </div>
        </div>

      <div class="modal-footer">
        <a href="#" class="btn btn-link link-secondary" data-bs-dismiss="modal" @click="setInit()">
          Cancel
        </a>
        <a href="#" class="btn btn-primary ms-auto" data-bs-dismiss="modal"  @click="onClickSubmit()">
          {{props.mode === 'new' ? '생성' : '수정'}}
        </a>
      </div>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Repository } from '../../type/type';
import { ref } from 'vue';
import { useToast } from 'vue-toastification';
import { registRepository, getRepositoryDetailInfo, updateRepository } from '@/api/repository';
import { onMounted } from 'vue';
import { computed } from 'vue';
import { watch } from 'vue';

const toast = useToast()
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
  await setInit();
})

const repositoryFormData = ref({} as Repository)
const writePolicy = ref("" as string)
const httpPort = ref(0 as number)
const httpsPort = ref(0 as number)

const setInit = async () => {
  if (props.mode === 'new') {
    repositoryFormData.value.name = ''
    repositoryFormData.value.format = 'raw'
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

/**x
 * @Title onClickSubmit
 * @Desc 
 *     1. 생성 / 수정 버튼 클릭시 동작
 *     2. 부모로 부터 받은 mode값에 따라서 생성/수정 Callback 함수 호출후 부모에게 repository 목록 api 호출  
 */
const onClickSubmit = async () => {
  repositoryFormData.value.storage = {
    "blobStoreName": "default",
    "strictContentTypeValidation": true,
    "writePolicy": writePolicy.value
  }

  if(repositoryFormData.value.format != "docker") {
    repositoryFormData.value.docker = null
  } else {
    repositoryFormData.value.docker = {
      "v1Enabled": true,
      "forceBasicAuth": true,
      "httpPort": httpPort.value,
      "httpsPort": httpsPort.value,
      "subdomain": "/test"
    }
  }

  if (props.mode === 'new') {
    await _registRepository().then(() => {
      emit('get-repository-list')
      setInit()
    })
  }
  else {
    await _updateRepository().then(() => {
      emit('get-repository-list')
      setInit()
    })  
  }
  
}

/**
 * @Title _registRepository
 * @Desc 생성 Callback 함수 / 생성 api 호출
 */
const _registRepository = async () => {
  const { data } = await registRepository("nexus", repositoryFormData.value)
  if (data)
    toast.success('등록되었습니다.')
  else
    toast.error('등록 할 수 없습니다.')
}

/**
 * @Title updateRepository
 * @Desc 수정 Callback 함수 / 수정 api 호출
 */
const _updateRepository = async () => {
  const { data } = await updateRepository("nexus", repositoryFormData.value)
  if (data)
    toast.success('등록되었습니다.')
  else
    toast.error('등록 할 수 없습니다.')
}

</script>

<!-- <style scoped>
.d-lb {
  display: inline-block;
}
.mr-5 {
   margin-right: 5px;
}
</style> -->