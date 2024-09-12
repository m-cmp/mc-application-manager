<template>
  <div class="tab-pane active show" id="tabs-pod">
    <!-- <h4>Pod</h4> -->
    <div class="mb-3">
      <label class="form-label">Pod Name</label>
      <input type="text" class="form-control w-33" name="example-text-input" v-model="podFormData.podName" placeholder="pod-01" />
    </div>
    <div class="mb-3">
      <label class="form-label">Namespace</label>
      <input type="text" class="form-control w-33" name="example-text-input" v-model="podFormData.namespace" placeholder="namespace" />
    </div>
    <div class="mb-3">
      <label class="form-label">Labels</label>
      <div class="generate-form" v-for="(item, idx) in podLabels" :key="idx">
        <input type="text" class="form-control w-33" name="example-password-input" v-model="item.key" placeholder="key" />
        <input type="text" class="form-control w-33" name="example-password-input" v-model="item.value" placeholder="value" />
        <div class="btn-list">
          <button class="btn btn-primary" @click="addLabel" style="text-align: center !important;">
          <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
            <path d="M12 5l0 14" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        <button class="btn btn-primary" @click="removeLabel(idx)">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        </div>
      </div>
    </div>
    <div class="mb-3">
      <label class="form-label">Image</label>
      <input type="text" class="form-control w-33" name="example-text-input" v-model="podFormData.image" placeholder="image" />
    </div>
    <div class="mb-3">
      <label class="form-label">containerPort</label>
      <div class="generate-form" v-for="(item, idx) in podContainerPort" :key="idx">
        <input type="text" class="form-control w-33" name="example-password-input" v-model="item.portNum" placeholder="portNum" />
        <input type="text" class="form-control w-33" name="example-password-input" v-model="item.name" placeholder="name" />
        <div class="btn-list">
          <button class="btn btn-primary" @click="addPort" style="text-align: center !important;">
          <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
            <path d="M12 5l0 14" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        <button class="btn btn-primary" @click="removePort(idx)">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        </div>
      </div>
    </div>
    <div class="mb-3">
      <label class="form-label">env</label>
      <div class="generate-form" v-for="(item, idx) in podEnv" :key="idx">
        <input type="text" class="form-control w-33" name="example-password-input" v-model="item.name" placeholder="name" />
        <input type="text" class="form-control w-33" name="example-password-input" v-model="item.value" placeholder="value" />
        <div class="btn-list">
          <button class="btn btn-primary" @click="addEnv" style="text-align: center !important;">
          <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
            <path d="M12 5l0 14" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        <button class="btn btn-primary" @click="removeEnv(idx)">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        </div>
      </div>
    </div>
    <div class="mb-3">
      <label class="form-label">volumeMounts</label>
      <div class="generate-form" v-for="(item, idx) in podVoumeMounts" :key="idx">
        <input type="text" class="form-control w-33" name="example-password-input" v-model="item.mountPath" placeholder="mountPath" />
        <input type="text" class="form-control w-33" name="example-password-input" v-model="item.name" placeholder="name" />
        <div class="btn-list">
          <button class="btn btn-primary" @click="addVm" style="text-align: center !important;">
          <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
            <path d="M12 5l0 14" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        <button class="btn btn-primary" @click="removeVm(idx)">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        </div>
      </div>
    </div>

    <div class="btn-list justify-content-end">
      <a class="btn btn-primary" @click="onClickPod">GENERATE</a>
    </div>
  </div>
  
  

</template>

<script setup lang="ts">
import type { Pod } from '@/views/type/type';
import { ref, reactive } from 'vue';
import { onMounted } from 'vue';
import { useToast } from 'vue-toastification';

/**
 * @Title formData 
 * @Desc pod 데이터
 */
 const podFormData = reactive({} as Pod)
 const podLabels = ref([])
 const podContainerPort = ref([])
 const podEnv = ref([])
 const podVoumeMounts = ref([])

 onMounted(async () => {
  await setInit();
})

const setInit = () => {
  podLabels.value.push({key: "", value:""})
  podContainerPort.value.push({portNum: "", name:""})
  podEnv.value.push({name: "", value:""})
  podVoumeMounts.value.push({mountPath: "", name:""})
  podFormData.podName = ""
  podFormData.namespace = ""
  podFormData.image = ""
}

const onClickPod = () => {
  console.log("onClickPod")
  podFormData.labels = podLabels.value
  podFormData.containerPort = podContainerPort.value
  podFormData.env = podEnv.value
  podFormData.volumeMounts = podVoumeMounts.value

  const param = { ...podFormData }

  console.log("param : ", param)
}

const addLabel = () => {
  podLabels.value.push({
    key: "", value:""
  })
}

const removeLabel = (idx: number) => {
  if(podLabels.value.length !== 1) {
    podLabels.value.splice(idx, 1)
  }
}

const addPort = () => {
  podContainerPort.value.push({
    portNum: "", name:""
  })
}

const removePort = (idx: number) => {
  if(podContainerPort.value.length !== 1) {
    podContainerPort.value.splice(idx, 1)
  }
}

const addEnv = () => {
  podEnv.value.push({
    name: "", value:""
  })
}

const removeEnv = (idx: number) => {
  if(podEnv.value.length !== 1) {
    podEnv.value.splice(idx, 1)
  }
}

const addVm = () => {
  podVoumeMounts.value.push({
    mountPath: "", name:""
  })
}

const removeVm = (idx: number) => {
  if(podVoumeMounts.value.length !== 1) {
    podVoumeMounts.value.splice(idx, 1)
  }
}

</script>
<style scoped>
.generate-form {
  width: 100% !important;
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}
</style>