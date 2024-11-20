
<template>
  <div class="page-header page-wrapper">
    <div class="row align-items-center">
      <div class="card-header d-flex" style="justify-content: space-between;">
        <h3 class="card-title"><strong>{{ props.headerTitle }}</strong></h3>
        <div class="btn-list">
          <a
            v-if="!props.popupFlag"
            class="btn btn-primary d-none d-sm-inline-block" 
            @click.prevent.stop="onClickNewbtn">
            <IconPlus class="icon icon-tabler icon-tabler-plus" color="white" :size="20" stroke-width="1" />
            {{ props.newBtnTitle }}
          </a>
          <a
            v-else
            class="btn btn-primary d-none d-sm-inline-block" 
            data-bs-toggle="modal" 
            :data-bs-target="props.popupTarget"
            @click.prevent.stop="onClickNewbtn">
            <IconPlus class="icon icon-tabler icon-tabler-plus" color="white" :size="20" stroke-width="1" />
            {{ props.newBtnTitle }}
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { IconPlus } from '@tabler/icons-vue';

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