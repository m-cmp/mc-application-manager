<template>
  <div ref="table" />
</template>

<script setup lang="ts">
import {ref, onMounted, watch} from 'vue';
import {TabulatorFull as Tabulator, type ColumnDefinition, type OptionsData} from 'tabulator-tables';
import { type Workflow } from '@/views/type/type'

interface Props {
  columns: Array<ColumnDefinition>
  tableData: any
}

const props = defineProps<Props>()
// const emit = defineEmits(['on-click-close-history'])

const table = ref(null) as any;
const tabulator = ref(null) as any;

watch(()=> props.columns, () => {
  makeTable()
})
watch(()=> props.tableData, () => {
  makeTable()
})

// onMounted(() => {
//   makeTable()
// })

const makeTable = () => {
  tabulator.value = new Tabulator(table.value, {
    data: props.tableData,
    reactiveData:true,
    columns: props.columns,
    pagination: true,
    paginationSize:6,
    paginationSizeSelector:[3, 6, 8, 10],
    movableColumns:true,
    paginationCounter:"rows",
});
}

</script>
<style>
.tabulator .tabulator-header {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  /* font-size: $textSize*0.8; */
  font-weight: unset;
  white-space: nowrap;
  overflow: hidden;
  outline: none;
  /* border-top: 1px solid $border-color; */
}

.tabulator .tabulator-header .tabulator-col .tabulator-col-content .tabulator-col-title{
	/* padding: 0 $headerMargin*3 0 $headerMargin*3; */
}

.tabulator-row .tabulator-cell {
  display: inline-block;
  position: relative;
  box-sizing: border-box;
  /* padding: $headerMargin*2 $headerMargin*4 $headerMargin*2 $headerMargin*4;
  border-bottom: 1px solid $border-color; */
  vertical-align: middle;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  outline: none;
}

.tabulator .tabulator-footer{
	font-weight: unset;
}

.tabulator-page{
  &.active{
    font-weight: bold;
  }
}
</style>