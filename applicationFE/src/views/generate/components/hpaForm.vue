<template>
  <div class="tab-pane" id="tabs-hpa">
    <div class="card">
      <div class="card-header">
        <h3 class="card-title">Metadata 영역</h3>
      </div>
      <div class="card-body">
        <div class="mb-3">
          <label class="form-label required">- Name</label>
          <input type="text" class="form-control w-33" name="example-text-input" v-model="metadata.name" placeholder="name" />
        </div>
        <div class="mb-3">
          <label class="form-label required">- Namespace</label>
          <input type="text" class="form-control w-33" name="example-text-input" v-model="metadata.namespace" placeholder="namespace" />
        </div>
        <div class="mb-3">
          <label class="form-label">- Labels</label>
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
      </div>
    </div>

    <div class="card mt-4">
      <div class="card-header">
        <h3 class="card-title">Spec 영역</h3>
      </div>
      <div class="card-body">
        <div class="mb-3">
          <label class="form-label">Scale Target</label>
        </div>
        <div class="row" style="width:68% !important">
          <div class="col">
            <label class="form-label required">- Api Version</label>
            <input type="text" class="form-control" v-model="scaleTargetRef.apiVersion" />
          </div>
          <div class="col">
            <label class="form-label required">- Kind</label>
            <input type="text" class="form-control" v-model="scaleTargetRef.kind" />
          </div>
        </div>
        <div class="row" style="width:68% !important">
          <div class="col">
            <label class="form-label required">- Name</label>
            <input type="text" class="form-control" v-model="scaleTargetRef.name" />
          </div>
        </div>
        <div class="row" style="width:68% !important">
          <div class="col">
            <label class="form-label required">- Min Replicas</label>
            <input type="text" class="form-control" v-model="spec.minReplicas" />
          </div>
        </div>
        <div class="row" style="width:68% !important">
          <div class="col">
            <label class="form-label required">- Max Replicas</label>
            <input type="text" class="form-control" v-model="spec.maxReplicas" />
          </div>
        </div>
        <div class="row" style="width:68% !important">
          <div class="col">
            <label class="form-label required">- CPU Percentage</label>
            <input type="text" class="form-control" v-model="spec.targetCPUUtilizationPercentage" />
          </div>
        </div>

      </div>
    </div>

    <div class="btn-list justify-content-end mt-4">
      <a class="btn btn-primary" @click="onClickHpa" data-bs-toggle='modal' data-bs-target='#modal-yaml'>GENERATE</a>
    </div>
    <YamlModal :yaml-data="yamlData" :title="title" />
  </div>
  
  
  

</template>

<script setup lang="ts">
import type { Hpa } from '@/views/type/type';
import { ref } from 'vue';
import { onMounted } from 'vue';
import { useToast } from 'vue-toastification';
import { generateYamlHpa } from '@/api/yaml.ts';
import YamlModal from './yamlModal.vue';

const toast = useToast()
/**
 * @Title formData 
 * @Desc pod 데이터
 */
 const title = ref("" as string)
 const hpaFormData = ref({} as Hpa)
 const metadata = ref({} as any)
 const hpaLabels = ref([] as any)
 const spec = ref({} as any)
 const scaleTargetRef = ref({} as any)
 const yamlData = ref("" as string)

 onMounted(async () => {
  await setInit();
})

const setInit = () => {
  title.value = "HPA"
  metadata.value = {
    name: "",
    namespace: "",
    labels: {}
  }
  hpaLabels.value.push({key: "", value:""})
  spec.value = {
    scaleTargetRef: {},
    minReplicas: "",
    maxReplicas: "",
    targetCPUUtilizationPercentage: ""
  }
  scaleTargetRef.value = {
    apiVersion: "",
    kind: "",
    name: ""
  }
}

const onClickHpa = async () => {

  const transformedObject = hpaLabels.value.reduce((acc: { [x: string]: any; }, item: { key: string | number; value: any; }) => {
    acc[item.key] = item.value;
    return acc;
  }, {});

  metadata.value.labels = transformedObject;
  spec.value.scaleTargetRef = scaleTargetRef.value;
  hpaFormData.value.metadata = metadata.value
  hpaFormData.value.spec = spec.value;

  const { data } = await generateYamlHpa(hpaFormData.value);
  yamlData.value = data;
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