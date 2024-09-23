<template>
  <div class="tab-pane" id="tabs-configMap">
    <div class="card">
      <div class="card-header">
        <h3 class="card-title">Metadata 영역</h3>
      </div>
      <div class="card-body">
        <div class="mb-3">
          <label class="form-label required">- Name</label>
          <input type="text" class="form-control w-33" name="example-text-input" v-model="metadata.name" placeholder="configMap-01" />
        </div>
        <div class="mb-3">
          <label class="form-label">- Namespace</label>
          <input type="text" class="form-control w-33" name="example-text-input" v-model="metadata.namespace" placeholder="namespace" />
        </div>
        <div class="mb-3">
          <label class="form-label">- Labels</label>
          <div class="generate-form" v-for="(item, idx) in ConfigMapLabels" :key="idx">
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
          <label class="form-label">- Data</label>
          <div class="generate-form" v-for="(item, idx) in configMapData" :key="idx">
            <input type="text" class="form-control w-33" name="example-password-input" v-model="item.key" placeholder="key" />
            <input type="text" class="form-control w-33" name="example-password-input" v-model="item.value" placeholder="value" />
            <div class="btn-list">
              <button class="btn btn-primary" @click="addData" style="text-align: center !important;">
              <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
                <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
                <path d="M12 5l0 14" />
                <path d="M5 12l14 0" />
              </svg>
            </button>
            <button class="btn btn-primary" @click="removeData(idx)">
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

    <div class="btn-list justify-content-end mt-4">
      <a class="btn btn-primary" @click="onClickDeploy" data-bs-toggle='modal' data-bs-target='#modal-config-map'>GENERATE</a>
    </div>
    <yamlModal :yaml-data="yamlData" :title="title" />
  </div>
  
  

</template>

<script setup lang="ts">
import type { ConfigMap } from '@/views/type/type';
import { ref } from 'vue';
import { onMounted } from 'vue';
import { generateYamlConfigmap } from '@/api/yaml.ts';
import { useToast } from 'vue-toastification';
import yamlModal from './configMapModal.vue';

const toast = useToast()
/**
 * @Title formData 
 * @Desc ConfigMap 데이터
 */
 const title = ref("" as string)
 const configMapFormData = ref({} as ConfigMap)
 const metadata = ref({} as any)
 const ConfigMapLabels = ref([] as any)
 const configMapData = ref([] as any)
 const yamlData = ref("" as string)

 onMounted(async () => {
  await setInit();
})

const setInit = () => {
  title.value = "ConfigMap"
  metadata.value = {
    name: "",
    namespace: "",
    labels: {}
  }
  ConfigMapLabels.value.push({key: "", value:""})
  configMapData.value.push({key: "", value:""})
}

const onClickDeploy = async () => {
  const transformedObject = ConfigMapLabels.value.reduce((acc: { [x: string]: any; }, item: { key: string | number; value: any; }) => {
    acc[item.key] = item.value;
    return acc;
  }, {});

  metadata.value.labels = transformedObject;

  const transformedData =  configMapData.value.reduce((acc: { [x: string]: any; }, item: { key: string | number; value: any; }) => {
    acc[item.key] = item.value;
    return acc;
  }, {});

  configMapFormData.value.metadata = metadata.value
  configMapFormData.value.data = transformedData;

  const { data } = await generateYamlConfigmap(configMapFormData.value);
  yamlData.value = data;
}

const addLabel = () => {
  ConfigMapLabels.value.push({
    key: "", value:""
  })
}

const removeLabel = (idx: number) => {
  if(ConfigMapLabels.value.length !== 1) {
    ConfigMapLabels.value.splice(idx, 1)
  }
}

const addData = () => {
  configMapData.value.push({
    key: "", value:""
  })
}

const removeData = (idx: number) => {
  if(configMapData.value.length !== 1) {
    configMapData.value.splice(idx, 1)
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