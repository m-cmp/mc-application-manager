<template>
  <div class="card card-flush w-100">
    <TableHeander 
      :header-title="'Workflow'"
      :new-btn-title="'New Workflow'"
      @click-new-btn="onClickNewBtn"
    />
    <Tabulator 
      :columns="columns"
      :table-data="workflowList">
    </Tabulator>
  </div>
</template>
<script setup lang="ts">
import TableHeander from '@/components/Table/TableHeader.vue'
import Tabulator from '@/components/Table/Tabulator.vue'
import { getWorkflowList } from '@/api/application.js'
import { onMounted } from 'vue';
import { ref } from 'vue';
import { type Workflow } from '@/views/type/type'
import { type ColumnDefinition } from 'tabulator-tables';
import router from '@/router';

const workflowList = ref([] as Array<Workflow>)
const columns = ref([] as Array<ColumnDefinition>)

onMounted(async () => {
  setColumns()
  await setWorkflowList()
})

const setWorkflowList = async () => {
  try {
    const { data } = await getWorkflowList()
    workflowList.value = data
  } catch(error) {
    console.log(error)
  }
}

const setColumns = () => {
  columns.value = [
    {
      title: "Workflow Name",
      field: "workflowName",
      width: 400
    },
    {
      title: "Workflow Purpose",
      field: "workflowPurpose",
      width: 200
    },
    {
      title: "jenkins Job Name",
      field: "jenkinsJobName",
      width: 400,
    },
    {
      title: "Created Date",
      field: "regDate",
      width: 300
    },
    {
      title: "Action",
      width: 400,
      formatter: editButtonFormatter,
      cellClick: function (e, cell) {
        const btnFlag = e.target?.getAttribute('id')
        if (btnFlag === 'edit-btn') {
          const workflowId = cell.getRow().getData().workflowId
          router.push('edit/' + workflowId)
        }
        else {
          const workflowId = cell.getRow().getData().workflowId
          alert('delete: ' + workflowId)
        }
      }
    }

  ]
}

const onClickNewBtn = () => {
  router.push('new')
}

const editButtonFormatter = () => {
  return "<div><button class='btn btn-primary d-none d-sm-inline-block' id='edit-btn' style='margin-right: 5px'>수정</button><button class='btn btn-primary d-none d-sm-inline-block' id='delete-btn'>삭제</button></div>";
}
</script>