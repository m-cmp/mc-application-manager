<template>
  <div class="page">
    <div class="page-wrapper">
      <div v-if="isRepositoryList" class="page-header d-print-none">
        <div class="container-xxl">
          <div class="row g-2 align-items-center">
            <div class="col d-flex">
              <h2 class="page-title">Repository</h2>
            </div>
            <div class="col-auto ms-auto">
              <div class="btn-list">
                <button
                  type="button"
                  class="btn btn-outline-primary page-header-action-btn"
                  data-bs-toggle="modal"
                  data-bs-target="#repositoryForm"
                  @click="onClickNewRepository">
                  <IconPlus class="icon icon-tabler icon-tabler-plus" :size="20" stroke-width="1" />
                  New Repository
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
              <RouterView v-slot="{ Component }">
                <component :is="Component" ref="repositoryViewRef" />
              </RouterView>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { IconPlus } from '@tabler/icons-vue'
import { RouterView, useRoute } from 'vue-router'

const route = useRoute()
const repositoryViewRef = ref<{ startCreate?: () => void } | null>(null)
const isRepositoryList = computed(() => route.name === 'repositoryList')

const onClickNewRepository = () => {
  repositoryViewRef.value?.startCreate?.()
}
</script>
