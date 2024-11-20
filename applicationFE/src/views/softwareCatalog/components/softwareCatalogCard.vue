<template>
  <div class="card mb-3">
    <div class="card-body cursor-pointer" @click="setSoftwareCatalogRefrence(idx)">
      <div class="grid">
        <div class="g-col-5">
          <img 
            :src="catalogInfo.catalogIcon" 
            class="rounded mb-3" 
            alt="Catalog Icon" 
            width="50" 
            height="50" 
            style="margin-left: 5px;"/>
          <div class="col-auto text-muted" style="align-items: center;">
            {{ catalogInfo.catalogCategory }}
          </div>
        </div>
        
        <div class="col g-col-7">
          <div>
            <b>{{ catalogInfo.catalogTitle }}</b>
            <div class="text-muted mt-3" style="height: 50px;">
              {{ catalogInfo.catalogSummary }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { SoftwareCatalog } from '@/views/type/type'

interface Props {
  catalogInfo: SoftwareCatalog
  idx: number | string
}
const props = defineProps<Props>()
const emit = defineEmits(['set-software-catalog-refrence'])

const catalogInfo = computed(() => props.catalogInfo)
const idx = computed(()=> props.idx)

const setSoftwareCatalogRefrence = async (idx: any) => {
  emit('set-software-catalog-refrence', idx)
}
</script>