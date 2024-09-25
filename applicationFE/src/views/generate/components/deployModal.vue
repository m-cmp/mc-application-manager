<template> 
  <div class="modal" id="modal-deploy" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">{{ props.title }}</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <div class="card">
            <div class="card-body">
              <h4>YAML</h4>
              <div>
                <pre>{{ data }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, computed, watch } from 'vue';
import { ref } from 'vue';
import { useToast } from 'vue-toastification';

interface Props {
  title: string
  yamlData: string
}
const props = defineProps<Props>()
const yamlData = computed(() => props.yamlData)
watch(yamlData, async () => {
  await setData();
});

const data = ref("" as string)

const setData = async () => {
  data.value = props.yamlData
}

</script>