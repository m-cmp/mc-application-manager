<template>
  <div class="tab-pane" id="tabs-hpa">
    <div class="mb-3">
      <label class="form-label">HPA Name</label>
      <input type="text" class="form-control w-33" name="example-text-input" v-model="hpaFormData.hpaName" placeholder="name" />
    </div>
    <div class="mb-3">
      <label class="form-label">Namespace</label>
      <input type="text" class="form-control w-33" name="example-text-input" v-model="hpaFormData.namespace" placeholder="namespace" />
    </div>
    <div class="mb-3">
      <label class="form-label">Labels</label>
      <div class="generate-form" v-for="(item, idx) in hpaLabels" :key="idx">
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
      <label class="form-label">Target</label>
      <div class="generate-form" >
        <select class="form-select" v-model="target.kind" style="width:33% !important">
          <option value="deployment">Deployment</option>
        </select>
        <input type="text" class="form-control w-33" name="example-password-input" v-model="target.name" placeholder="name" />
      </div>
    </div>
    <div class="mb-3">
      <label class="form-label">Min Replicas</label>
      <input type="number" class="form-control w-33" name="example-text-input" v-model="hpaFormData.minReplicas" placeholder="image" />
    </div>
    <div class="mb-3">
      <label class="form-label">Max Replicas</label>
      <input type="number" class="form-control w-33" name="example-text-input" v-model="hpaFormData.maxReplicas" placeholder="image" />
    </div>
    <div class="mb-3">
      <label class="form-label">Metric</label>
      <div class="generate-form">
        <select class="form-select" v-model="metric.type" style="width:33% !important">
          <option value="cpu">CPU</option>
          <option value="memory">MEMORY</option>
        </select>
        <input type="number" class="form-control w-33" name="example-password-input" v-model="metric.targetAverageUtilization" placeholder="value" max="100" />
      </div>
    </div>

    <div class="btn-list justify-content-end">
      <a class="btn btn-primary" @click="onClickHpa" data-bs-toggle='modal' data-bs-target='#modal-hpa'>GENERATE</a>
    </div>
    <hpaModal />
  </div>
  
  
  

</template>

<script setup lang="ts">
import type { Hpa } from '@/views/type/type';
import { ref } from 'vue';
import { onMounted } from 'vue';
import { useToast } from 'vue-toastification';
import { generateYamlHpa } from '@/api/yaml.ts';
import hpaModal from './hpaModal.vue';
import { bootstrap } from "bootstrap"

const toast = useToast()
/**
 * @Title formData 
 * @Desc pod 데이터
 */
 const hpaFormData = ref({} as Hpa)
 const hpaLabels = ref([{}])
 const target = ref({})
 const metric = ref({})

 onMounted(async () => {
  await setInit();
})

const setInit = () => {
  hpaLabels.value.push({key: "", value:""})
  target.value = {kind: "deployment", name: ""}
  metric.value = {type: "cpu", targetAverageUtilization: 0}
  hpaFormData.value.hpaName = ""
  hpaFormData.value.namespace = ""
  hpaFormData.value.minReplicas = 0
  hpaFormData.value.maxReplicas = 0
}

const onClickHpa = async () => {
  hpaFormData.value.target = target.value
  hpaFormData.value.metric = metric.value

  const transformedObject = hpaLabels.value.reduce((acc, item) => {
    acc[item.key] = item.value;
    return acc;
  }, {});

  hpaFormData.value.labels = transformedObject;
  const { data } = await generateYamlHpa(hpaFormData.value);

}

const addLabel = () => {
  hpaLabels.value.push({
    key: "", value:""
  })
}

const removeLabel = (idx: number) => {
  if(hpaLabels.value.length !== 1) {
    hpaLabels.value.splice(idx, 1)
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