<template>
  <div class="card w-100" ref="workflowForm">
    <div class="card-header">
      <div class="card-title">
        <h1>Repository Detail</h1>
      </div>
    </div>
    <div 
      class="card-body">
      <div class="card-title">
        <div class="mb-3">
          <label class="form-label">Name</label>
          <div class="grid gap-0 column-gap-3">
            <input type="text" ref="repositoryName" class="form-control p-2 g-col-11" v-model="repositoryDetail.name" disabled />
          </div>
        </div>

        <div class="mb-3">
          <label class="form-label">Format</label>
          <div class="grid gap-0 column-gap-3">
            <input type="text" ref="repositoryFormat" class="form-control p-2 g-col-11" v-model="repositoryDetail.format" disabled />
          </div>
        </div>

        <div class="mb-3">
          <label class="form-label required">URL(Path)</label>
          <div class="grid gap-0 column-gap-3">
            <input type="text" ref="repositoryFormat" class="form-control p-2 g-col-11" v-model="repositoryDetail.url" disabled />
          </div>
        </div>

        <div class="mb-3">
          <label class="form-label required">Type(hosted)</label>
          <div class="grid gap-0 column-gap-3">
            <input type="text" ref="repositoryFormat" class="form-control p-2 g-col-11" v-model="repositoryDetail.type" disabled />
          </div>
        </div>

        <div class="mb-3">
          <!-- <div class="btn-list">
            <button class='btn btn-primary d-none d-sm-inline-block' style="margin-left: auto; margin-bottom:10px;" data-bs-toggle='modal' data-bs-target='#uploadComponent' :disabled="repositoryDetail.format == 'docker'">
              File Upload
            </button>      
          </div> -->
          <Tabulator 
            :columns="columns"
            :table-data="componentList">
          </Tabulator>
        </div>

        <div class="row align-items-center">
          <div id="gap" class="col" />
          <div class="col-auto ms-auto">
            <div class="btn-list">
              <button class="btn btn-outline-primary" @click="onClickList">
                Back to List
              </button>
            </div>
          </div>
        </div>
      </div>

      <DeleteComponent 
        :component-name="selectComponentName"
        :component-id="selectComponentId"
        @get-detail="_getDetailInfo"/>

      <UploadComponent 
        :repository-name="repositoryDetail.name"
        :format="repositoryDetail.format"
        @get-detail="_getDetailInfo"/>

    </div>      
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { onMounted } from 'vue';
import { watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getRepositoryDetailInfo, getComponentList } from '@/api/repository';
import { type Repository, type Component } from '@/views/type/type'
import { useToast } from 'vue-toastification';
// @ts-ignore
import _ from 'lodash';
import Tabulator from '@/components/Table/Tabulator.vue'
import { type ColumnDefinition } from 'tabulator-tables';
import DeleteComponent from './components/deleteComponent.vue';
import UploadComponent from './components/uploadComponent.vue';

const toast = useToast()
const route = useRoute();
const router = useRouter();
const props = defineProps<{ embedded?: boolean; repositoryName?: string }>()
const emit = defineEmits<{ (e: 'back-to-list'): void }>()
const repositoryDetail = ref({} as Repository)

const componentList = ref([] as Array<Component>)
const columns = ref([] as Array<ColumnDefinition>)

onMounted(async () => {
  setColumns()
  if (!props.embedded) {
    await _getDetailInfo()
  }
})

const _getDetailInfo = async (name?: string) => {
  const routeName = route.params.repositoryName as string | undefined
  const finalName = name ?? (props.embedded ? props.repositoryName : routeName)
  if (!finalName) return
  const { data } = await getRepositoryDetailInfo("nexus", finalName);
  repositoryDetail.value = data;
  await _getComponentInfo()
}

watch(
  () => props.repositoryName,
  async (newVal) => {
    if (props.embedded && newVal) {
      await _getDetailInfo(newVal)
    }
  },
  { immediate: true }
)

const _getComponentInfo = async () => {
  const { data } = await getComponentList("nexus", repositoryDetail.value.name);
  componentList.value = data;
}

const selectComponentId = ref('' as string)
const selectComponentName = ref('' as string)
const setColumns = () => {
  columns.value = [
    {
      title: "Name",
      field: "name",
      width: '15%'
    },
    // {
    //   title: "Format",
    //   field: "format",
    //   width: '15%'
    // },
    {
      title: "URL(Path)",
      field: "assets",
      width: '50%',
      formatter: function(cell) {
        const assets = cell.getValue();
        if (assets && assets.length > 0) {
            const dowonloadUrl = assets[0].downloadUrl;
            return dowonloadUrl;
        }
        return "N/A";
      }
    },
    {
      title: "Size",
      field: "assets",
      width: '15%',
      formatter: function(cell) {
        const assets = cell.getValue();
        if (assets && assets.length > 0) {
            const fileSize = assets[0].fileSize;
            return `${(fileSize / 1024).toFixed(2)} KB`; // 파일 크기를 KB 단위로 포맷
        }
        return "N/A";
      }
    },
    {
      title: "Action",
      width: '20%',
      formatter: downloadDeleteButtonFormatter,
      cellClick: function (e, cell) {
        const target = e.target as HTMLElement;
        const btnFlag = target?.getAttribute('id')
        
        if (btnFlag === 'download-btn') {
          console.log("download")
          let fileData = cell.getRow().getData()
          downloadUrl(fileData)
        }
        else {
          selectComponentId.value = cell.getRow().getData().id
          selectComponentName.value = cell.getRow().getData().name
        }
      }
    }
  ]
}

const downloadDeleteButtonFormatter = () => {
  return `
  <div>
  <button
      class='btn btn-outline-danger d-none d-sm-inline-block'
      id='delete-btn'
      data-bs-toggle='modal' 
      data-bs-target='#deleteComponent'>
      Delete
    </button>
    <button
      class='btn btn-outline-primary d-none d-sm-inline-block me-1'
      id='download-btn'>
      Download
    </button>
  </div>`;
}

const downloadUrl = (data: any) => {
  const url = data.assets[0].downloadUrl;
  const link = document.createElement('a');
  link.href = url;
  link.download = '';

  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

const onClickList = () => {
  if (props.embedded) {
    emit('back-to-list')
  } else {
    router.push('/web/repository/list')
  }
}

</script>