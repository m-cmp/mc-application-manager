<template>
  <div class="modal" id="deleteComponent" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">

        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        <div class="modal-status bg-danger"></div>
        <div class="modal-body text-left py-4">
          <!-- OSS Title -->
          <h3 class="mb-5">
            Component 삭제
          </h3>

          <h4>{{ props.componentName }}을(를) 정말 삭제하시겠습니까?</h4>

        </div>

        <div class="modal-footer">
          <a href="#" class="btn btn-link link-secondary" data-bs-dismiss="modal">
            Cancel
          </a>
          <a href="#" class="btn btn-primary ms-auto" data-bs-dismiss="modal"  @click="onClickDelete()">
            삭제
          </a>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useToast } from 'vue-toastification';
import { deleteComponent } from '@/api/repository';

const toast = useToast()
/**
 * @Title Props / Emit
 */
interface Props {
  componentName: string
  componentId: string
}
const props = defineProps<Props>()
const emit = defineEmits(['get-detail'])

/**
 * @Title onClickDelete
 * @Desc 삭제 버튼 클릭시 동작 / 삭제 api 호출
 */
const onClickDelete = async () => {
  const { data } = await deleteComponent("nexus", props.componentId)
  if (data)
    toast.success('삭제되었습니다.')
  else
    toast.error('삭제하지 못했습니다.')
    emit('get-detail')
}
</script>