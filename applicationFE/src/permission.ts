import router from "./router/index";
import { useUserStore } from '@/stores/user'

router.beforeEach(async (to, from, next) => {
  console.log('## to ### : ', to)
  console.log('## from ### : ', from)

  window.addEventListener("message", async function (event) {
    let data
    if (event.data.accessToken === undefined) {
      data = {
        accessToken: "accesstokenExample", // 있는지 확인
        workspaceInfo: {
          id: "8b2df1f9-b937-4861-b5ce-855a41c346bc",
          name: "workspace2",
          description: "workspace2 desc",
          created_at: "2024-06-18T00:10:16.192337Z",
          updated_at: "2024-06-18T00:10:16.192337Z"
        },
        projectInfo: {
          id: "1e88f4ea-d052-4314-80a4-9ac3f6691feb",
          ns_id: "ns01",
          mci_id: "mci01",
          cluster_id: "cluster01",
          name: "ns01",
          description: "ns01 desc",
          created_at: "2024-06-18T00:28:57.094105Z",
          updated_at: "2024-06-18T00:28:57.094105Z"
        },
        operationId: "op1"
      }
    }
    else {
      data = event.data;
    }
    try {
      // let responseData = await sendData(data);
      // console.log(responseData);
      // if (responseData && responseData.requestUrl) {
      //     window.location = responseData.requestUrl;
      // } else {
      //     console.error("requestUrl not found in response data");
      // }
      console.log(data)
      const userinfo = useUserStore();
      userinfo.setUser(data)
    } catch (error) {
      console.error("Error in processing message:", error);
    }
  });

  next();
});