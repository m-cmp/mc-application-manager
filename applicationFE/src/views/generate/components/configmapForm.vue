<template>
  <div class="tab-pane" id="tabs-configMap">
    <div class="mb-3">
      <label class="form-label">Deployment Name</label>
      <input type="text" class="form-control w-33" name="example-text-input" v-model="deploymentFormData.deployName" placeholder="deploy-01" />
    </div>
    <div class="mb-3">
      <label class="form-label">Namespace</label>
      <input type="text" class="form-control w-33" name="example-text-input" v-model="deploymentFormData.namespace" placeholder="namespace" />
    </div>
    <div class="mb-3">
      <label class="form-label">Labels</label>
      <div class="generate-form" v-for="(item, idx) in deploymentLabels" :key="idx">
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
      <label class="form-label">Selector</label>
      <div class="generate-form" v-for="(item, idx) in deploySelector" :key="idx">
        <input type="text" class="form-control w-33" name="example-password-input" v-model="item.key" placeholder="key" />
        <input type="text" class="form-control w-33" name="example-password-input" v-model="item.value" placeholder="value" />
        <div class="btn-list">
          <button class="btn btn-primary" @click="addSelector" style="text-align: center !important;">
          <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
            <path d="M12 5l0 14" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        <button class="btn btn-primary" @click="removeSelector(idx)">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        </div>
      </div>
    </div>
    <div class="mb-3">
      <label class="form-label">Replicas</label>
      <input type="text" class="form-control w-33" name="example-text-input" v-model="deploymentFormData.namespace" placeholder="namespace" />
    </div>
    <div class="mb-3">
      <label class="form-label">Image</label>
      <input type="text" class="form-control w-33" name="example-text-input" v-model="deploymentFormData.image" placeholder="image" />
    </div>
    <div class="mb-3">
      <label class="form-label">containerPort</label>
      <div class="generate-form" v-for="(item, idx) in deployContainerPort" :key="idx">
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
      <div class="generate-form" v-for="(item, idx) in deployEnv" :key="idx">
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
      <div class="generate-form" v-for="(item, idx) in deployVoumeMounts" :key="idx">
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
      <a class="btn btn-primary" @click="onClickDeploy">GENERATE</a>
    </div>
  </div>
  
  

</template>

<script setup lang="ts">
import type { Deployment } from '@/views/type/type';
import { ref, reactive } from 'vue';
import { onMounted } from 'vue';
import { useToast } from 'vue-toastification';

/**
 * @Title formData 
 * @Desc pod 데이터
 */
 const deploymentFormData = reactive({} as Deployment)
 const deploymentLabels = ref([])
 const deploySelector = ref([])
 const deployContainerPort = ref([])
 const deployEnv = ref([])
 const deployVoumeMounts = ref([])

 onMounted(async () => {
  await setInit();
})

const setInit = () => {
  deploymentLabels.value.push({key: "", value:""})
  deploySelector.value.push({key: "", value:""})
  deployContainerPort.value.push({portNum: "", name:""})
  deployEnv.value.push({name: "", value:""})
  deployVoumeMounts.value.push({mountPath: "", name:""})
  deploymentFormData.deployName = ""
  deploymentFormData.namespace = ""
  deploymentFormData.image = ""
}

const onClickDeploy = () => {
  console.log("onClickDeploy")
  deploymentFormData.labels = deploymentLabels.value
  deploymentFormData.selector = deploySelector.value
  deploymentFormData.containerPort = deployContainerPort.value
  deploymentFormData.env = deployEnv.value
  deploymentFormData.volumeMounts = deployVoumeMounts.value

  const param = { ...deploymentFormData }

  console.log("param : ", param)
}

const addLabel = () => {
  deploymentLabels.value.push({
    key: "", value:""
  })
}

const removeLabel = (idx: number) => {
  if(deploymentLabels.value.length !== 1) {
    deploymentLabels.value.splice(idx, 1)
  }
}

const addSelector = () => {
  deploySelector.value.push({
    key: "", value:""
  })
}

const removeSelector = (idx: number) => {
  if(deploySelector.value.length !== 1) {
    deploySelector.value.splice(idx, 1)
  }
}

const addPort = () => {
  deployContainerPort.value.push({
    portNum: "", name:""
  })
}

const removePort = (idx: number) => {
  if(deployContainerPort.value.length !== 1) {
    deployContainerPort.value.splice(idx, 1)
  }
}

const addEnv = () => {
  deployEnv.value.push({
    name: "", value:""
  })
}

const removeEnv = (idx: number) => {
  if(deployEnv.value.length !== 1) {
    deployEnv.value.splice(idx, 1)
  }
}

const addVm = () => {
  deployVoumeMounts.value.push({
    mountPath: "", name:""
  })
}

const removeVm = (idx: number) => {
  if(deployVoumeMounts.value.length !== 1) {
    deployVoumeMounts.value.splice(idx, 1)
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