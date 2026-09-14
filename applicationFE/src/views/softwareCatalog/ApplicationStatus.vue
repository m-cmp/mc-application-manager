<template>
  <div class="page">
    <div class="page-wrapper">
      <div class="page-header d-print-none">
        <div class="container-xxl">
          <div class="row g-2 align-items-center">
            <div class="col d-flex">
              <h2 class="page-title">App Status</h2>
            </div>
            <div class="col-auto ms-auto">
              <div class="btn-list">
                <span class="me-2 d-none d-md-inline">{{ refreshTime }}</span>
                <button
                  type="button"
                  class="btn btn-outline-primary page-header-action-btn"
                  @click="refreshApplications">
                  <IconRefresh class="icon icon-tabler" :size="20" stroke-width="1" />
                  Refresh
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="page-body">
        <div class="container-xxl">
          <div class="row">
            <div class="col-lg-12">
              <ApplicationStatusList
                ref="applicationStatusListRef"
                :ns-id="nsId"
                @refresh-time-updated="refreshTime = $event" />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { IconRefresh } from '@tabler/icons-vue'
import ApplicationStatusList from '@/views/softwareCatalog/components/applicationStatusList.vue'

const userinfo = useUserStore()
const nsId = computed(() => userinfo.getNsId() || '')
const refreshTime = ref('')
const applicationStatusListRef = ref<InstanceType<typeof ApplicationStatusList> | null>(null)

const refreshApplications = () => {
  applicationStatusListRef.value?.refresh()
}
</script>
