<template>
  <div class="card card-flush w-100">
    <TableHeander 
      :header-title="'Repository'"
      :new-btn-title="'New Repository'"
      :popup-flag="true"
      :popup-target="'#repositoryForm'"
      @click-new-btn="onClickNewBtn"
    />
    <Tabulator 
      :columns="columns"
      :table-data="repositoryList">
    </Tabulator>

    <RepositoryForm 
      :mode="formMode"
      :repository-name="selectRepositoryName"
      @get-repository-list="_getRepositoryList"/>

    <DeleteRepository 
      :repository-name="selectRepositoryName"
      @get-repository-list="_getRepositoryList"/>

  </div>
</template>
<script setup lang="ts">
import TableHeander from '@/components/Table/TableHeader.vue'
import Tabulator from '@/components/Table/Tabulator.vue'
import { getRepositoryList } from '@/api/repository'
import { onMounted } from 'vue';
import { ref } from 'vue';
import { type Repository } from '@/views/type/type'
import { type ColumnDefinition } from 'tabulator-tables';
import { useToast } from 'vue-toastification';
import router from '@/router';
import RepositoryForm from './components/repositoryForm.vue';
import DeleteRepository from './components/deleteRepository.vue';

const toast = useToast()
/**
 * @Title repositoryList / columns
 * @Desc 
 *    repositoryList : repository 목록 저장
 *    columns : 목록의 컬럼 저장
 */
const repositoryList = ref([] as Array<Repository>)
const columns = ref([] as Array<ColumnDefinition>)

/**
 * @Title Life Cycle
 * @Desc 컬럼 set Callback 함수 호출 / repositoryList Callback 함수 호출
 */
onMounted(async () => {
  setColumns()
  await _getRepositoryList()
})

/**
 * @Title _getRepositoryList
 * @Desc repository List Callback 함수 / repository List api 호출
 */
const _getRepositoryList = async () => {
  try {
    const { data } = await getRepositoryList("nexus")
    repositoryList.value = data
  } catch(error) {
    console.log(error)
    toast.error('데이터를 가져올 수 없습니다.')
  }
}

/**
 * @Title selectRepositoryName / setColumns
 * @Desc
 *    selectRepositoryName : 삭제를 위한 선택된 row의 ossName저장
 *    setColumns : 컬럼 set Callback 함수
 */
const selectRepositoryName = ref('' as string)
const setColumns = () => {
  columns.value = [
    {
      title: "Name",
      field: "name",
      width: 400,
      cellClick: function (e, cell) {
        e.stopPropagation();
        selectRepositoryName.value = cell.getRow().getData().name
        router.push('/web/repository/detail/' + selectRepositoryName.value)
      }
    },
    {
      title: "Format",
      field: "format",
      width: 300
    },
    {
      title: "URL",
      field: "url",
      width: 410
    },
    {
      title: "Type(hosted)",
      field: "type",
      width: 400
    },
    {
      title: "Action",
      width: 400,
      formatter: editDeleteButtonFormatter,
      cellClick: function (e, cell) {
        const target = e.target as HTMLElement;
        const btnFlag = target?.getAttribute('id')
        selectRepositoryName.value = cell.getRow().getData().name

        if (btnFlag === 'edit-btn') {
          formMode.value = 'edit'
        }
        else {
          selectRepositoryName.value = cell.getRow().getData().name
        }
      }
    }
  ]
}

/**
 * @Title editButtonFormatter
 * @Desc 수정 / 삭제 버튼 Formatter
 */
const editDeleteButtonFormatter = () => {
  return `
  <div>
    <button
      class='btn btn-primary d-none d-sm-inline-block mr-5'
      id='edit-btn'
      data-bs-toggle='modal' 
      data-bs-target='#repositoryForm'>
      Update
    </button>
    <button
      class='btn btn-danger d-none d-sm-inline-block'
      id='delete-btn'
      data-bs-toggle='modal' 
      data-bs-target='#deleteRepository'>
      Delete
    </button>
  </div>`;
}

/**
 * @Title formMode
 * @Desc 기본값 new / repositoryForm에 생성/수정 을 알려주는 값
 */
const formMode = ref('new')

/**
 * @Title onClickNewBtn
 * @Desc repository 생성버튼 클릭시 동작하는 함수 (formMode set)
 */
const onClickNewBtn = () => {
  formMode.value = 'new'
}


</script>