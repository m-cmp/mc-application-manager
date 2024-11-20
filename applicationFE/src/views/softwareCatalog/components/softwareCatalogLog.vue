<template>
    <div class="modal" id="softwareCatalogLog" tabindex="-1">
      <div class="modal-dialog modal-xl" role="document">
        <div class="modal-content">
  
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          <div class="modal-body text-left py-4">
            <h3 class="mb-5">
              Build Log
              <div v-if="!firstLoadData" class="spinner-border" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
            </h3>
            <div>
              <div v-if="buildLogList.length <= 0">
                <p class="text-secondary">No Data</p>
              </div>
              <div v-else v-for="buildLog in buildLogList" :key="buildLog.buildIdx">
                <div class="card mb-3">
                  <div class="card-header" @click="onClickedBuildIdx(buildLog.buildIdx)" style="cursor: pointer;">
                    <h3 class="card-title">{{ buildLog.buildIdx }}</h3>
                  </div>
                  <div v-if="clickedBuildIdx === buildLog.buildIdx" class="card-body">
                    <textarea :value="buildLog.buildLog" disabled style="width: 100%;" rows="20"></textarea>
                    <!-- <p class="text-secondary">{{buildLog.buildLog}}</p> -->
                  </div>
                </div>
              </div>
            </div>
          </div>
  
          <div class="modal-footer">
            <a class="btn btn-link link-secondary" data-bs-dismiss="modal" @click="setClear">
              Cancel
            </a>
          </div>
  
        </div>
      </div>
    </div>
  </template>
  
<script setup lang="ts">
// import type { Oss, OssType } from '@/views/type/type';
import { ref } from 'vue';
import { useToast } from 'vue-toastification';
import { computed } from 'vue';
import { watch } from 'vue';
import { getBuildLogList } from '@/api/softwareCatalog';
  
  const toast = useToast()
  /**
   * @Title Props / Emit
   */
  interface Props {
    jobName: string
  }
  const props = defineProps<Props>()
  
  const firstLoadData = ref(false as boolean)

  const jobName = computed(() => props.jobName);
  watch(jobName, async () => {
    firstLoadData.value = false
    await setInit();
  });

  /**
   * @Title 초기화 Method
   * @Desc 
   */
  const buildLogList = ref([] as any)
  const setInit = async () => {
      buildLogList.value = []
  
      const response = await getBuildLogList(jobName.value)
      buildLogList.value = response.data.data;
      firstLoadData.value = true;

  }
  
  const setClear = () => {
    buildLogList.value = []
    clickedBuildIdx.value = 1
  }
  
  const clickedBuildIdx = ref(1 as number)
  const onClickedBuildIdx = (buildIdx: number) => {
    if (clickedBuildIdx.value === buildIdx) {
      clickedBuildIdx.value = 0
    }
    else {
      clickedBuildIdx.value = buildIdx
    }
  }
  </script>