<template>
    <div class="page-header">
    <div class="row align-items-center">
      <div class="col">
        <h2 class="page-title">
          {{ props.headerTitle }}
        </h2>
      </div>
      <div class="col-auto ms-auto">
        <div class="btn-list">
          <a
            v-if="!props.popupFlag"
            class="btn btn-primary d-none d-sm-inline-block" 
            @click.prevent.stop="onClickNewbtn">
            <svg xmlns="http://www.w3.org/2000/svg" class="icon icon-tabler icon-tabler-plus" width="24" height="24" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" fill="none" stroke-linecap="round" stroke-linejoin="round">
              <path stroke="none" d="M0 0h24v24H0z" fill="none"></path>
              <path d="M12 5l0 14"></path>
              <path d="M5 12l14 0"></path>
            </svg>
            {{ props.newBtnTitle }}
          </a>
          <a
            v-else
            class="btn btn-primary d-none d-sm-inline-block" 
            data-bs-toggle="modal" 
            :data-bs-target="props.popupTarget"
            @click.prevent.stop="onClickNewbtn">
            <svg xmlns="http://www.w3.org/2000/svg" class="icon icon-tabler icon-tabler-plus" width="24" height="24" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" fill="none" stroke-linecap="round" stroke-linejoin="round">
              <path stroke="none" d="M0 0h24v24H0z" fill="none"></path>
              <path d="M12 5l0 14"></path>
              <path d="M5 12l14 0"></path>
            </svg>
            {{ props.newBtnTitle }}
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">

interface Props {
  headerTitle: string
  newBtnTitle: string
  popupFlag: boolean
  popupTarget: string
}

const props = defineProps<Props>()
const emit = defineEmits(['click-new-btn'])

const onClickNewbtn = () => {
  emit('click-new-btn')
}
</script>