<template>
  <div class="modal" id="software-card-detail" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">
            {{ catalogInfo.catalogTitle}}
          </h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close" ></button>
        </div>
        <div class="modal-body" style="max-height: calc(100vh - 200px);overflow-y: auto;">
          <div class="datagrid">
            <div class="datagrid-item">
              <div class="d-flex justify-content-space-between">
                <div class="col-lg-11 d-flex justify-content-start mb-5">
                  <div class="me-30" style="margin-right: 30px;">
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

                  <div class="mt-3">
                    <b>{{ catalogInfo.catalogTitle }}</b>
                    <div class="text-muted">
                      {{ catalogInfo.catalogSummary }}
                    </div>
                  </div>
                </div>

                <div class="col-lg-1 lh-1">
                  <div class="dropdown">
                    <a href="#" class="link-secondary" data-bs-toggle="dropdown">
                      <IconDots 
                        class="icon icon-tabler" 
                        color="black" 
                        :size="20" 
                        stroke-width="2" 
                        />
                    </a>
                    <div class="dropdown-menu dropdown-menu-end">
                      <a class="dropdown-item" @click="onClickUpdate(catalogInfo.catalogIdx)" data-bs-toggle="modal"
                        data-bs-target="#modal-form">Update</a>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="pt-0">
            <div class="mb-5" 
              style="white-space: pre-wrap;" 
              v-html="formattedText(catalogInfo.catalogDescription)" />

              <div class="datagrid">
                <div class="datagrid-item mb-5">
                  <div class="datagrid-title">Ref Information</div>
                  <div class="datagrid-content">
                    <template v-if="hasProperty(catalogInfo.refData, 'HOMEPAGE')">
                      <template v-for="homepage in catalogInfo.refData.HOMEPAGE" :key="homepage">
                        <div>
                          <a @click="goToPage(homepage.referenceValue)" class="btn">{{ homepage.referenceValue }}</a>
                        </div>
                      </template>
                    </template>
                  </div>
                </div>
                
                <div class="datagrid-item">
                  <div class="datagrid-title">TAGS</div>
                  <div class="datagrid-content">
                      <div v-if="hasProperty(catalogInfo.refData, 'TAG')">
                        <div class="d-inline" v-for="tag in catalogInfo.refData.TAG" :key="tag">
                          <div class="d-inline">
                            <span>#{{ tag.referenceValue }} &nbsp;</span>
                          </div>
                        </div>
                      </div>
                  </div>
                </div>
              </div>
                
              <div class="datagrid">
                <div class="datagrid-item mb-5">
                  <div class="datagrid-title">Recommended Spec</div>
                  <div class="datagrid-content">
                    <template
                      v-if="
                        catalogInfo.recommendedCpu &&
                        catalogInfo.recommendedMemory &&
                        catalogInfo.recommendedDisk">
                      <button class="btn btn-sm" style="margin-right: 5px;">
                        CPU : {{ catalogInfo.recommendedCpu }}
                      </button>
                      <button class="btn btn-sm" style="margin-right: 5px;">
                        MEMORY : {{ catalogInfo.recommendedMemory }}
                      </button>
                      <button class="btn btn-sm" style="margin-right: 5px;">
                        DISK : {{ catalogInfo.recommendedDisk }}
                      </button>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="footer">
            <div class="col-auto me-auto fs-3">
              <div class="d-flex justify-content-center">
                <button 
                  class="btn btn-primary me-md-2 mb-2" 
                  data-bs-toggle='modal' 
                  data-bs-target='#install-form'
                  @click="onClickDeploy('Application Installation')">INSTALL</button>
                <button 
                  class="btn btn-danger me-md-2 mb-2" 
                  data-bs-toggle='modal' 
                  data-bs-target='#install-form'
                  @click="onClickDeploy('Application Uninstallation')">UNINSTALL</button>
              </div>
            </div>

          </div>
        </div>
      </div>
    </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import type { SoftwareCatalog } from '@/views/type/type'
import { IconDots } from '@tabler/icons-vue';
import { getSoftwareCaltalogDetail } from '@/api/softwareCatalog';

interface Props {
  catalogIdx: number | string
}
const props = defineProps<Props>()
const emit = defineEmits(['set-software-catalog-refrence', 'on-click-deploy'])

const catalogIdx = computed(()=> props.catalogIdx)
const catalogInfo = ref({} as SoftwareCatalog)

watch(() => catalogIdx.value, async () => {
  await getCatalogDatail(catalogIdx.value)
})

onMounted(async () => {
  await getCatalogDatail(catalogIdx.value)
})

const getCatalogDatail = async (catalogIdx : number | string) => {
  await getSoftwareCaltalogDetail(catalogIdx).then(({ data }) => {

    const splitUrl = window.location.host.split(':');
    const baseUrl = window.location.protocol + '//' + splitUrl[0] + ':18084'

    data.catalogIcon = baseUrl + data.catalogIcon
      
    catalogInfo.value = data
  })
}

const setSoftwareCatalogRefrence = async (idx: any) => {
  emit('set-software-catalog-refrence', idx)
}

const formMode = ref('new')

const onClickUpdate = (idx:number) => {
  formMode.value = "update";
  // selectCatalogIdx.value = idx;
}

const formattedText = (text: string) => {
  if (text === undefined) return "";
  else return text.replace(/\\n|\n/g, '<br/>');
}

const hasProperty = (data: any, prop: any) => {
  if (data === undefined) return null;
  return Object.prototype.hasOwnProperty.call(data, prop);
}

const goToPage = (url:string) => {
  window.open(url)
}

const onClickDeploy = (action: string) => {
  emit('on-click-deploy', action)
}
</script>

<style scoped>
.me-30 {
  margin-right: 30px;
}
</style>