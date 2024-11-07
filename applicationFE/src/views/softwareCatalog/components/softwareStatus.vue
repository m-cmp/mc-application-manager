<template>
  <div class="modal" id="status-modal" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Software Status [{{ nsName }}]</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body" style="max-height: calc(100vh - 200px);overflow-y: auto;">
          <div class="mb-3">
            <!-- ------------------------------------------- INFRA (VM/K8S) ------------------------------------------- -->
            <div v-for="infra in softwareStatusList" :key="infra.type" @click="onClickInfra
              ">
              <div class="tree tree-item d-flex justify-content-start">
                <i v-if="infra.showFlag" class="bi bi-caret-down-fill" style="font-size: x-large;" />
                <i v-else class="bi bi-caret-right-fill" style="font-size: x-large;" />
                <p >{{ infra.type }}</p>
              </div>
            
              <!-- ------------------------------------------- MCI ------------------------------------------- -->
              <div v-show="infra.showFlag" v-if="infra.type === 'VM'">
                <div class="tree" v-for="mci in infra.list" :key="mci.mciId">
                  <div class="d-flex justify-content-start">
                    <i v-if="mci.showFlag" class="bi bi-caret-down-fill" style="font-size: x-large;" />
                    <i v-else class="bi bi-caret-right-fill" style="font-size: x-large;" />
                    <p class="tree tree-item">{{mci.mciName}}</p>
                  </div>
                  
                  <!-- ------------------------------------------- VM  ------------------------------------------- -->
                  <div class="tree" v-for="vm in mci.vmList" :key="vm.vmId">
                    <div class="d-flex justify-content-start">
                      <i class="bi bi-caret-down-fill" style="font-size: x-large;" />
                      <p class="tree tree-item">{{ vm.vmName }}</p>
                    </div>

                    <!-- ------------------------------------------- APP  ------------------------------------------- -->
                      <div class="tree tree-item" v-for="app in vm.installedApplication" :key="app.applicationId">
                        <i class="bi bi-dot" style="font-size: x-large;" />
                        <p calss="tree tree-item" style="display: inline !important; margin-left: 10px !important;"> {{ app.applicationName }} </p>
                        <button v-if="app.applicationStatus !== null" class="btn btn-success btn-sm d-inline">{{ app.applicationStatus }}</button>
                        <button v-else class="btn btn-danger btn-sm d-inline">UNDEFINED</button>
                      </div>
                    <!-- <div class="tree" v-for="app in vm.installedApplication" :key="app.applicationId">
                      

                    </div> -->
                  </div>
                </div>
              </div>
              
              <!-- ------------------------------------------- K8S (PMK) ------------------------------------------- -->
              <div v-show="infra.showFlag" v-if="infra.type === 'K8S'">
                <div class="tree" v-for="pmk in infra.list" :key="pmk.pmkid">
                  <i v-if="infra.showFlag" class="bi bi-caret-down-fill" style="font-size: x-large;" />
                  <i v-else class="bi bi-caret-right-fill" style="font-size: x-large;" />
                  <p class="tree">{{pmk.pmkName}}</p>
                </div>  
              </div>
              

            </div>
          </div>
        </div>
        <div class="modal-footer">
          <a class="btn btn-link link-secondary" data-bs-dismiss="modal" @click="setInit">
            Cancel
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Repository } from '../../type/type';
import { ref } from 'vue';
import { useToast } from 'vue-toastification';
import { onMounted, onUnmounted, watch, computed } from 'vue';
import axios from 'axios'

/**
 * @Title Props / Emit
 */
interface Props {
  nsName: string
  list: Array<any>
}
const props = defineProps<Props>()

const isOpen = ref(false as boolean)
const toggle = () => {
  isOpen.value = !isOpen.value
}

watch(props.list, () => {
  console.log(props.list)
  props.list.forEach((list) => {
    console.log(list)
  })
})

















const toast = useToast()
const softwareStatusList = ref([] as any)


const splitUrl = window.location.host.split(':');
const baseUrl = window.location.protocol + '//' + splitUrl[0] + ':18084'
// const baseUrl = "http://15.164.227.13:18084";
// const baseUrl = "http://192.168.6.30:18084";


const emit = defineEmits(['reset-init-flag'])
// watch(initFlag, async () => {
//   await setInit();
// });

onMounted(async () => {
  const modalElement = document.getElementById("status-modal");
  modalElement?.addEventListener("shown.bs.modal", async () => { await setInit() });
})
const setInit = async () => {
  await getSoftwareStatusList()
}
const getSoftwareStatusList = async () => {
  softwareStatusList.value = [
    {
      type: "VM",
      list: [
        {
          mciId: 1,
          mciName: "mci01",
          vmList: [
            {
              vmId: 1,
              vmName: "vm01-1",
              installedApplication: [
                {
                  applicationId: 1,
                  applicationName: "Nginx",
                  applicationStatus: "RUNNING"
                },
                {
                  applicationId: 2,
                  applicationName: "MariaDB",
                  applicationStatus: "RESTARTING"
                },
                {
                  applicationId: 3,
                  applicationName: "REDIS",
                  applicationStatus: "STOP"
                },
                {
                  applicationId: 4,
                  applicationName: "TOMCAT",
                  applicationStatus: null
                }
              ]
            }
          ]
        },
        {
          mciId: 2,
          mciName: "mci02",
          vmList: [
            {
              vmId: 1,
              vmName: "vm02-1",
              installedApplication: [
                {
                  applicationId: 1,
                  applicationName: "Nginx",
                  applicationStatus: "RUNNING"
                },
                {
                  applicationId: 2,
                  applicationName: "MariaDB",
                  applicationStatus: "RESTARTING"
                },
                {
                  applicationId: 3,
                  applicationName: "REDIS",
                  applicationStatus: "STOP"
                },
                {
                  applicationId: 4,
                  applicationName: "TOMCAT",
                  applicationStatus: null
                }
              ]
            }
          ]
        }
      ],
      
    },
    {
      type: "K8S",
      list: [
        {
          pmkId: 1,
          pmkName: "pmk01",
          installedApplication: [
            {
              applicationId: 1,
              applicationName: "Nginx",
              applicationStatus: "RUNNING"
            },
            {
              applicationId: 2,
              applicationName: "MariaDB",
              applicationStatus: "RESTARTING"
            },
            {
              applicationId: 3,
              applicationName: "REDIS",
              applicationStatus: "STOP"
            },
            {
              applicationId: 4,
              applicationName: "TOMCAT",
              applicationStatus: null
            }
          ]
        }
      ]
    },
  ]

  softwareStatusList.value.forEach((infra:any) => {
    infra.showFlag = false

    if (infra.type === 'VM') {
      infra.forEach((list:any) => {
        list.showFlag = false

        list.forEach((vmList:any) => {
          vmList.showFlag = false
        })
      }) 
    }
    else if (infra.type === 'K8S') {
      infra.forEach((list:any) => {
        list.showFlag = false
      }) 
    }
  })
  //   try {
  //     await axios.get(baseUrl + '/catalog/software/' + props.catalogIdx).then(({ data }) => {
  //       catalogDto.value = data
  //       data.catalogRefData.forEach((catalogRef: any) => {
  //         if (catalogRef.referenceType !== null)
  //           catalogRef.referenceType = catalogRef.referenceType.toUpperCase()
  //       })
  //       refData.value = data.catalogRefData;
  //     })
  //   } catch(error) {
  //       console.log(error)
  //       toast.error('데이터를 가져올 수 없습니다.')
  //   }

}
</script>
<style scoped>
.tree {
  cursor: pointer;
  margin-left: 20px;
}
.tree-item {
  font-size: 1.5rem;
  font-weight: 600;
}
</style>