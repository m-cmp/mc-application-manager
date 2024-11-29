import{c as oe,I as ee}from"./IconPlus-WJaQoBnv.js";import{d as se,u as ne,c as j,r as f,o as ve,a as i,b as e,t as $,j as D,e as r,v as w,F as C,f as T,g as h,q as W,h as c,p as ie,k as ce,w as ae,i as z,s as Y,C as he,l as de}from"./index-D3T95vNm.js";import{_ as be}from"./lodash-CAolBhd9.js";import{s as _}from"./request-C2GHfbVL.js";import{_ as re}from"./_plugin-vue_export-helper-DlAUqK2U.js";/**
 * @license @tabler/icons-vue v3.21.0 - MIT
 *
 * This source code is licensed under the MIT license.
 * See the LICENSE file in the root directory of this source tree.
 */var dl=oe("outline","dots","IconDots",[["path",{d:"M5 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",key:"svg-0"}],["path",{d:"M12 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",key:"svg-1"}],["path",{d:"M19 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",key:"svg-2"}]]);/**
 * @license @tabler/icons-vue v3.21.0 - MIT
 *
 * This source code is licensed under the MIT license.
 * See the LICENSE file in the root directory of this source tree.
 */var le=oe("outline","minus","IconMinus",[["path",{d:"M5 12l14 0",key:"svg-0"}]]);const fe=()=>_.get("/cbtumblebug/ns"),_e=l=>_.get(`/cbtumblebug/ns/${l}/mci`),ge=l=>_.get(`/cbtumblebug/ns/${l.nsId}/mci/${l.mciId}`),ye=l=>_.get(`/cbtumblebug/ns/${l}/k8scluster`),ke=l=>_.get(`/catalog/software/?title=${l}`),we=l=>_.get(`/catalog/software/${l}`),rl=l=>_.get(`/search/dockerhub/${l}`),ul=l=>_.get(`/search/artifacthub/${l}`),Se=l=>_.get(`/applications/vm/deploy?namespace=${l.namespace}&mciId=${l.mciId}&vmId=${l.vmId}&catalogId=${l.catalogId}&servicePort=${l.servicePort}`),Ie=l=>_.get(`/applications/vm/action?operation=${l.operation}&applicationStatusId=${l.applicationStatusId}&reason=${l.reason}`),$e=l=>_.get(`/applications/k8s/deploy?namespace=${l.namespace}&clusterName=${l.clusterName}&catalogId=${l.catalogId}`),Ce=l=>_.get(`/applications/k8s/action?operation=${l.operation}&applicationStatusId=${l.applicationStatusId}&reason=${l.reason}`),Ue=l=>_.get(`/applications/vm/check?namespace=${l.namespace}&mciId=${l.mciName}&vmId=${l.vmName}&catalogId=${l.catalogId}`),Ve=l=>_.get(`/applications/k8s/check?namespace=${l.namespace}&clusterName=${l.clusterName}&catalogId=${l.catalogId}`),ml=l=>_.get(`/ape/log/${l}`);function Me(l){return _.post("/catalog/software",l)}function xe(l){return _.put("/catalog/software",l)}function pl(){return _.get("/applications/groups")}const g=l=>(ie("data-v-49902fd3"),l=l(),ce(),l),Re={class:"modal",id:"install-form",tabindex:"-1"},Ae={class:"modal-dialog modal-lg",role:"document"},Ne={class:"modal-content"},De={class:"modal-header"},Te={class:"modal-title"},Ee={class:"modal-body",style:{"max-height":"calc(100vh - 200px)","overflow-y":"auto"}},Pe={class:"mb-3"},Le=g(()=>e("label",{class:"form-label"},"Target Infra",-1)),Fe={key:0,class:"text-muted"},Ke={key:1,class:"text-muted"},Oe=["value"],qe={class:"mb-3"},Be=g(()=>e("label",{class:"form-label"},"Namespace",-1)),He={key:0,class:"text-muted"},ze={key:1,class:"text-muted"},Ye=["value"],je={value:"selectNsId"},We={class:"mb-3"},Ge=g(()=>e("label",{class:"form-label"},"MCI Name",-1)),Je={key:0,class:"text-muted"},Qe={key:1,class:"text-muted"},Xe=["disabled"],Ze=["value"],et={class:"mb-3"},tt=g(()=>e("label",{class:"form-label"},"VM Name",-1)),at=g(()=>e("p",{class:"text-muted"}," Select the virtual machine (VM) within the chosen multi-cloud infrastructure where the application will be deployed",-1)),lt=["disabled"],ot=["value"],st={class:"mb-3"},nt=g(()=>e("label",{class:"form-label"},"Application",-1)),it=g(()=>e("p",{class:"text-muted"},"Select the application",-1)),ct={class:"mb-3"},dt=g(()=>e("label",{class:"form-label"},"Port",-1)),rt=g(()=>e("p",{class:"text-muted"},"Please enter a port accessible from the outside",-1)),ut={class:"mb-3"},mt=g(()=>e("label",{class:"form-label"},"Namespace",-1)),pt={key:0,class:"text-muted"},vt={key:1,class:"text-muted"},ht=["value"],bt={value:"selectNsId"},ft={class:"mb-3"},_t=g(()=>e("label",{class:"form-label"},"ClusterName",-1)),gt={key:0,class:"text-muted"},yt={key:1,class:"text-muted"},kt=["disabled"],wt=["value"],St={class:"mb-3"},It=g(()=>e("label",{class:"form-label"},"Helm chart",-1)),$t=g(()=>e("p",{class:"text-muted"},"Select the application",-1)),Ct={key:0,class:"mb-3"},Ut=g(()=>e("label",{class:"form-label"},"HPA",-1)),Vt={class:"d-flex justigy-content-between"},Mt=g(()=>e("label",{class:"form-label required"}," minReplicas ",-1)),xt=g(()=>e("label",{class:"form-label required"}," maxReplicas ",-1)),Rt=g(()=>e("label",{class:"form-check-label mb-2"}," CPU (%) ",-1)),At=g(()=>e("label",{class:"form-check-label mb-2"}," MEMORY (%) ",-1)),Nt={class:"modal-footer d-flex justify-content-between"},Dt=["disabled"],Tt=["disabled"],Et=se({__name:"applicationInstallationForm",props:{nsId:{},title:{}},setup(l){const U=ne(),x=l,p=j(()=>x.title),O=f([]),V=f([]),S=f([]),E=f([]),R=f([]),a=f(""),u=f(""),b=f(""),y=f(""),I=f({}),F=f([]),A=f(""),N=f(""),q=f(""),M=f(!0);ve(async()=>{document.getElementById("install-form").addEventListener("show.bs.modal",async()=>{await B(),await J()})});const B=async()=>{a.value="",u.value="",b.value="",y.value="",I.value={},Q(),G(),await d()},J=async()=>{await ke("").then(({data:m})=>{R.value=m})},Q=()=>{O.value=[{key:"VM",value:"VM"},{key:"k8s",value:"K8S"}]},G=()=>{p.value==="Application Uninstallation"?M.value=!1:M.value=!0},d=async()=>{await fe().then(async({data:m})=>{V.value=m,V.value.length>0&&(u.value=V.value[0].name),be.isEmpty(u.value)||(a.value==="VM"?await o():a.value==="K8S"&&await P())})},o=async()=>{await _e(u.value).then(async({data:m})=>{S.value=m,S.value.length>0?(b.value=S.value[0].name,await n()):b.value=""})},n=async()=>{const m={nsId:u.value,mciId:b.value};await ge(m).then(({data:s})=>{E.value=s.vm,S.value.length>0?y.value=E.value[0].name:y.value=""})},P=async()=>{await ye(u.value).then(({data:m})=>{F.value=m,F.value.length>0?A.value=F.value[0].name:A.value=""})},k=async()=>{await o(),K()},X=async()=>{await n(),K()},Z=async()=>{await P(),K()},K=()=>{p.value==="Application Installation"?M.value=!0:p.value==="Application Uninstallation"&&(M.value=!1)},ue=async()=>{let m={},s={};a.value==="VM"?(N.value.split(",").map(t=>t.toLowerCase().trim()),m={namespace:u.value,mciId:b.value,vmId:y.value,catalogId:L.value,servicePort:q.value},p.value=="Application Installation"?s=await Se(m):s=await Ie(m),s.data?U.success("SUCCESS"):U.error("FAIL")):a.value==="K8S"&&(N.value.split(",").map(t=>t.toLowerCase().trim()),m={namespace:u.value,clusterName:A.value,catalogId:L.value},p.value=="Application Installation"?s=await $e(m):s=await Ce(m),s.data?U.success("SUCCESS"):U.error("FAIL"))},me=async()=>{a.value==="VM"||a.value==="K8S"?pe().then(m=>{let s=!0;if(m===null){U.error("Please select all items");return}else if(m===!1){let t="";a.value==="VM"?t="VM":a.value==="K8S"&&(t="CLUSTER");const H="Your selected "+t+" has lower specifications than recommended. Would you like to continue with the installation?";s=confirm(H)}s&&(U.success("Please click RUN"),M.value=!1)}):U.error("Please Select Infra")},pe=async()=>{let m=!1;if(a.value==="VM"){if(u.value===""||b.value===""||y.value===""||L.value===0)return null;{const s={namespace:u.value,mciName:b.value,vmName:y.value,catalogId:L.value};await Ue(s).then(({data:t})=>{m=t})}}else if(a.value==="K8S"){if(u.value===""||A.value===""||L.value===0){U.error("Please select all items");return}const s={namespace:u.value,clusterName:A.value,catalogId:L.value};await Ve(s).then(({data:t})=>{m=t})}return m},L=f(0),te=()=>{p.value==="Application Installation"&&(M.value=!0),R.value.forEach(m=>{if(N.value===m.title){L.value=m.id;return}})};return(m,s)=>(c(),i("div",Re,[e("div",Ae,[e("div",Ne,[e("div",De,[e("h5",Te,$(p.value),1),e("button",{type:"button",class:"btn-close","data-bs-dismiss":"modal","aria-label":"Close",onClick:B})]),e("div",Ee,[e("div",Pe,[Le,p.value=="Application Installation"?(c(),i("p",Fe," Select the Infra what is the Infra will be installed ")):p.value=="Application Uninstallation"?(c(),i("p",Ke," Select the Infra what is the Infra will be uninstalled ")):D("",!0),r(e("select",{class:"form-select",id:"infra","onUpdate:modelValue":s[0]||(s[0]=t=>a.value=t),onClick:K},[(c(!0),i(C,null,T(O.value,t=>(c(),i("option",{value:t.value,key:t.value},$(t.value),9,Oe))),128))],512),[[w,a.value]])]),a.value=="VM"?(c(),i(C,{key:0},[e("div",qe,[Be,p.value=="Application Installation"?(c(),i("p",He," Select the namespace where the application will be installed")):p.value=="Application Uninstallation"?(c(),i("p",ze," Select the namespace where the application will be uninstalled")):D("",!0),V.value.length>0?r((c(),i("select",{key:2,class:"form-select",id:"namesapce","onUpdate:modelValue":s[1]||(s[1]=t=>u.value=t),onChange:k},[(c(!0),i(C,null,T(V.value,t=>(c(),i("option",{value:t.name,key:t.name},$(t.name),9,Ye))),128))],544)),[[w,u.value]]):r((c(),i("select",{key:3,class:"form-select",id:"namesapce","onUpdate:modelValue":s[2]||(s[2]=t=>u.value=t),onChange:k},[e("option",je,$(u.value),1)],544)),[[w,u.value]])]),e("div",We,[Ge,p.value=="Application Installation"?(c(),i("p",Je," Select the multi-cloud infrastructure information where the application will be deployed")):p.value=="Application Uninstallation"?(c(),i("p",Qe," Remove the application and associated resources from the multi-cloud infrastructure")):D("",!0),r(e("select",{class:"form-select",id:"mci-name",disabled:u.value=="","onUpdate:modelValue":s[3]||(s[3]=t=>b.value=t),onChange:X},[(c(!0),i(C,null,T(S.value,t=>(c(),i("option",{value:t.id,key:t.name},$(t.name),9,Ze))),128))],40,Xe),[[w,b.value]])]),e("div",et,[tt,at,r(e("select",{class:"form-select",id:"mci-name",disabled:b.value=="","onUpdate:modelValue":s[4]||(s[4]=t=>y.value=t)},[(c(!0),i(C,null,T(E.value,t=>(c(),i("option",{value:t.id,key:t.name},$(t.name),9,ot))),128))],8,lt),[[w,y.value]])]),e("div",st,[nt,it,r(e("select",{class:"form-select","onUpdate:modelValue":s[5]||(s[5]=t=>N.value=t),onChange:te},[(c(!0),i(C,null,T(R.value,(t,H)=>(c(),i("option",{key:H},$(t.title),1))),128))],544),[[w,N.value]])]),e("div",ct,[dt,rt,r(e("input",{type:"number",class:"form-control",placeholder:"8080","onUpdate:modelValue":s[6]||(s[6]=t=>q.value=t)},null,512),[[h,q.value]])])],64)):a.value=="K8S"?(c(),i(C,{key:1},[e("div",ut,[mt,p.value=="Application Installation"?(c(),i("p",pt,"Select the namespace where the application will be installed")):p.value=="Application Uninstallation"?(c(),i("p",vt,"Select the namespace where the application will be uninstalled")):D("",!0),V.value.length>0?r((c(),i("select",{key:2,class:"form-select",id:"namesapce","onUpdate:modelValue":s[7]||(s[7]=t=>u.value=t),onChange:Z},[(c(!0),i(C,null,T(V.value,t=>(c(),i("option",{value:t.name,key:t.name},$(t.name),9,ht))),128))],544)),[[w,u.value]]):r((c(),i("select",{key:3,class:"form-select",id:"namesapce","onUpdate:modelValue":s[8]||(s[8]=t=>u.value=t),onChange:k},[e("option",bt,$(u.value),1)],544)),[[w,u.value]])]),e("div",ft,[_t,p.value=="Application Installation"?(c(),i("p",gt,"Select the name of the cluster where the application will be deployed")):p.value=="Application Uninstallation"?(c(),i("p",yt,"Remove the application and associated resources from the multi-cloud infrastructure")):D("",!0),r(e("select",{class:"form-select",id:"mci-name",disabled:u.value=="","onUpdate:modelValue":s[9]||(s[9]=t=>A.value=t)},[(c(!0),i(C,null,T(F.value,t=>(c(),i("option",{value:t.id,key:t.name},$(t.name),9,wt))),128))],8,kt),[[w,A.value]])]),e("div",St,[It,$t,r(e("select",{class:"form-select","onUpdate:modelValue":s[10]||(s[10]=t=>N.value=t),onChange:te},[(c(!0),i(C,null,T(R.value,(t,H)=>(c(),i("option",{key:H},$(t.title),1))),128))],544),[[w,N.value]])]),p.value=="Application Installation"?(c(),i("div",Ct,[Ut,e("div",Vt,[e("div",null,[Mt,r(e("input",{type:"number",class:"form-control w-90-per",placeholder:"1","onUpdate:modelValue":s[11]||(s[11]=t=>I.value.hpaMinReplicas=t)},null,512),[[h,I.value.hpaMinReplicas]])]),e("div",null,[xt,r(e("input",{type:"number",class:"form-control w-90-per",placeholder:"10","onUpdate:modelValue":s[12]||(s[12]=t=>I.value.hpaMaxReplicas=t)},null,512),[[h,I.value.hpaMaxReplicas]])]),e("div",null,[Rt,r(e("input",{type:"number",class:"form-control w-80-per d-inline",placeholder:"60","onUpdate:modelValue":s[13]||(s[13]=t=>I.value.hpaCpuUtilization=t)},null,512),[[h,I.value.hpaCpuUtilization]]),W(" % ")]),e("div",null,[At,r(e("input",{type:"number",class:"form-control w-80-per d-inline",placeholder:"80","onUpdate:modelValue":s[14]||(s[14]=t=>I.value.hpaMemoryUtilization=t)},null,512),[[h,I.value.hpaMemoryUtilization]]),W(" % ")])])])):D("",!0)],64)):D("",!0)]),e("div",Nt,[e("a",{class:"btn btn-link link-secondary","data-bs-dismiss":"modal",onClick:B}," Cancel "),e("div",null,[p.value=="Application Installation"?(c(),i("button",{key:0,class:"btn btn-danger ms-auto me-1",onClick:me,disabled:!M.value}," Spec Check ",8,Dt)):D("",!0),e("button",{class:"btn btn-primary ms-auto","data-bs-dismiss":"modal",onClick:ue,disabled:M.value}," RUN ",8,Tt)])])])])]))}}),vl=re(Et,[["__scopeId","data-v-49902fd3"]]),v=l=>(ie("data-v-54c32f26"),l=l(),ce(),l),Pt={class:"modal",id:"modal-form",tabindex:"-1"},Lt={class:"modal-dialog modal-lg",role:"document"},Ft={class:"modal-content"},Kt=v(()=>e("div",{class:"modal-header"},[e("h5",{class:"modal-title"}," Create New Software catalog "),e("button",{type:"button",class:"btn-close","data-bs-dismiss":"modal","aria-label":"Close"})],-1)),Ot={class:"modal-body",style:{"max-height":"calc(100vh - 200px)","overflow-y":"auto"}},qt={class:"mb-3"},Bt=v(()=>e("label",{class:"form-label"},"Title",-1)),Ht={class:"mb-3"},zt=v(()=>e("label",{class:"form-label"},"Summary",-1)),Yt={class:"mb-3"},jt=v(()=>e("label",{class:"form-label"},"Category",-1)),Wt=de('<option value="SERVER" selected data-v-54c32f26>SERVER</option><option value="WAS" data-v-54c32f26>WAS</option><option value="DB" data-v-54c32f26>DB</option><option value="UTIL" data-v-54c32f26>UTIL</option><option value="OBSERVABILITY" data-v-54c32f26>OBSERVABILITY</option>',5),Gt=[Wt],Jt={class:"mb-3"},Qt=v(()=>e("label",{class:"form-label"},"Description",-1)),Xt=v(()=>e("label",{class:"form-label"},"Spec",-1)),Zt={class:"mb-5"},ea={class:"accordion",id:"accordion"},ta={class:"accordion-item"},aa=v(()=>e("h2",{class:"accordion-header",id:"headingRecommendSpec"},[e("button",{class:"accordion-button required",type:"button","data-bs-toggle":"collapse","data-bs-target":"#recommendedSpec","aria-expanded":"true","aria-controls":"recommendedSpec"}," Recommended Spec ")],-1)),la={id:"recommendedSpec",class:"accordion-collapse collapse",show:"","aria-labelledby":"headingRecommendSpec","data-bs-parent":"#accordion"},oa={class:"accordion-body"},sa={class:"d-flex justify-content-between"},na=v(()=>e("label",{class:"form-label required"},"CPU",-1)),ia=v(()=>e("label",{class:"form-label required"},"MEMORY",-1)),ca=v(()=>e("label",{class:"form-label required"},"DISK",-1)),da={class:"accordion-item"},ra=v(()=>e("h2",{class:"accordion-header",id:"headingMinimumSpec"},[e("button",{class:"accordion-button",type:"button","data-bs-toggle":"collapse","data-bs-target":"#minimumspec","aria-expanded":"true","aria-controls":"minimumspec"}," Minimun Spec ")],-1)),ua={id:"minimumspec",class:"accordion-collapse collapse",show:"","aria-labelledby":"headingMinimumSpec","data-bs-parent":"#accordion"},ma={class:"accordion-body"},pa={class:"d-flex justify-content-between"},va=v(()=>e("label",{class:"form-label required"},"CPU",-1)),ha=v(()=>e("label",{class:"form-label required"},"MEMORY",-1)),ba=v(()=>e("label",{class:"form-label required"},"DISK",-1)),fa={class:"accordion-item"},_a=v(()=>e("h2",{class:"accordion-header",id:"headingPort"},[e("button",{class:"accordion-button",type:"button","data-bs-toggle":"collapse","data-bs-target":"#port","aria-expanded":"true","aria-controls":"port"}," Port ")],-1)),ga={id:"port",class:"accordion-collapse collapse",show:"","aria-labelledby":"headingPort","data-bs-parent":"#accordion"},ya={class:"accordion-body"},ka=v(()=>e("label",{class:"form-label required"},"Port",-1)),wa={class:"d-flex justify-content-between mb-3"},Sa={class:"btn-list"},Ia={class:"accordion-item"},$a={class:"accordion-header",id:"headingHpa"},Ca={class:"accordion-button d-inline",type:"button","data-bs-toggle":"collapse","data-bs-target":"#hpa","aria-expanded":"true","aria-controls":"hpa"},Ua=["disabled"],Va={id:"hpa",class:"accordion-collapse collapse","aria-labelledby":"headingHpa","data-bs-parent":"#accordion"},Ma={class:"accordion-body"},xa={class:"d-flex justify-content-between"},Ra=v(()=>e("label",{class:"form-label required"},"minReplicas",-1)),Aa=["disabled"],Na=v(()=>e("label",{class:"form-label required"},"maxReplicas",-1)),Da=["disabled"],Ta=v(()=>e("div",null,[e("label",{class:"form-check-label"},"CPU (%)")],-1)),Ea=["disabled"],Pa=v(()=>e("div",null,[e("label",{class:"form-check-label"},"MEMORY (%)")],-1)),La=["disabled"],Fa={class:"col-lg-6"},Ka={class:"mb-3"},Oa=v(()=>e("label",{class:"form-label"},"Reference",-1)),qa=["onUpdate:modelValue"],Ba=de('<option value="URL" data-v-54c32f26>URL</option><option value="MANIFEST" data-v-54c32f26>MANIFEST</option><option value="WORKFLOW" data-v-54c32f26>WORKFLOW</option><option value="IMAGE" data-v-54c32f26>IMAGE</option><option value="HOMEPAGE" data-v-54c32f26>HOMEPAGE</option><option value="TAG" data-v-54c32f26>TAG</option><option value="ETC" data-v-54c32f26>ETC</option>',7),Ha=[Ba],za={class:"col-lg-6"},Ya={class:"mb-3"},ja=v(()=>e("label",{class:"form-label"}," ",-1)),Wa=["onUpdate:modelValue"],Ga={class:"mb-3"},Ja={class:"input-form"},Qa=["onUpdate:modelValue"],Xa={class:"btn-list"},Za=["onClick"],el={class:"modal-footer"},tl={key:0},al={key:1},ll=se({__name:"softwareCatalogForm",props:{mode:{},catalogIdx:{},repositoryApplicationInfo:{},repositoryName:{}},emits:["get-list"],setup(l,{emit:U}){const x=ne(),p=l,O=U,V=j(()=>p.catalogIdx),S=j(()=>p.mode),E=j(()=>p.repositoryApplicationInfo),R=j(()=>p.repositoryName),a=f({}),u=f([]),b=f(!1);ae(()=>V.value,async()=>{await y()},{deep:!0}),ae(()=>E.value,async()=>{await y()},{deep:!0});const y=async()=>{S.value==="update"?await N():(R.value==="dockerhub"?I(E.value,R.value):R.value==="artifacthub"?F(E.value,R.value):A(),u.value=[],u.value.push({refId:0,refValue:"",refDesc:"",refType:""}))},I=(d,o)=>{a.value={title:d.name,description:d.short_description,category:"",summary:d.short_description,sourceType:o,logoUrlLarge:d.logo_url.large,logoUrlSmall:d.logo_url.small,minCpu:0,minMemory:0,minDisk:0,recommendedCpu:0,recommendedMemory:0,recommendedDisk:0,cpuThreshold:0,memoryThreshold:0,minReplicas:0,maxReplicas:0,catalogRefData:[],hpaEnabled:!1,defaultPort:0,packageInfo:{packageType:"DOCKER",packageName:d.id,packageVersion:"latest",repositoryUrl:"https://hub.docker.com/_/"+d.name,dockerImageId:"",dockerPublisher:d.publisher.name,dockerCreatedAt:G(d.created_at),dockerUpdatedAt:G(d.updated_at),dockerShortDescription:d.short_description,dockerSource:d.source}}},F=(d,o)=>{a.value={title:d.name,description:d.description,category:"",summary:"",sourceType:o,logoUrlLarge:"",logoUrlSmall:"",minCpu:0,minMemory:0,minDisk:0,recommendedCpu:0,recommendedMemory:0,recommendedDisk:0,cpuThreshold:0,memoryThreshold:0,minReplicas:0,maxReplicas:0,catalogRefData:[],hpaEnabled:!1,defaultPort:0,helmChart:{id:0,catalogId:0,chartName:"string",chartVersion:"string",chartRepositoryUrl:"string",valuesFile:"string",packageId:"string",normalizedName:"string",hasValuesSchema:!0,repositoryName:"string",repositoryOfficial:!0,repositoryDisplayName:"string"}}},A=()=>{a.value={title:"",description:"",category:"",summary:"",sourceType:"",logoUrlLarge:"",logoUrlSmall:"",minCpu:0,minMemory:0,minDisk:0,recommendedCpu:0,recommendedMemory:0,recommendedDisk:0,cpuThreshold:0,memoryThreshold:0,minReplicas:0,maxReplicas:0,defaultPort:0,catalogRefData:[],hpaEnabled:!1}},N=async()=>{try{await we(V.value).then(({data:d})=>{a.value=d,a.value.catalogRefs.length===0&&(a.value.catalogRefs=[{refId:0,refValue:"",refDesc:"",refType:""}]),a.value.hpaEnabled&&(b.value=!0),d.catalogRefs.forEach(o=>{o.refType!==null&&(o.refType=o.refType.toUpperCase())}),u.value=d.catalogRefs})}catch(d){console.log(d),x.error("데이터를 가져올 수 없습니다.")}},q=()=>{a.value.defaultPort.push("")},M=d=>{a.value.defaultPort.length!==1&&a.value.defaultPort.splice(d,1)},B=()=>{u.value.push({refId:0,refValue:"",refDesc:"",refType:"URL"})},J=d=>{u.value.length!==1&&u.value.splice(d,1)},Q=async()=>{b.value&&(a.value.hpaEnabled=!0),a.value.catalogRefData=u.value,S.value=="new"?await Me(a.value).then(({data:d})=>{d?d===null?(x.error("Regist Failed"),y()):(x.success("Regist Success"),O("get-list")):(x.error("Regist Failed"),y())}):await xe(a.value).then(({data:d})=>{d?d===null?(x.error("Update Failed"),y()):(x.success("Update Success"),O("get-list")):(x.error("Update Failed"),y())})},G=d=>{const o=new Date(d),n=o.getFullYear(),P=String(o.getMonth()+1).padStart(2,"0"),k=String(o.getDate()).padStart(2,"0"),X=String(o.getHours()).padStart(2,"0"),Z=String(o.getMinutes()).padStart(2,"0"),K=String(o.getSeconds()).padStart(2,"0");return`${n}-${P}-${k} ${X}:${Z}:${K}`};return(d,o)=>(c(),i("div",Pt,[e("div",Lt,[e("div",Ft,[Kt,e("div",Ot,[e("div",qt,[Bt,r(e("input",{type:"text",class:"form-control",id:"sc-title",name:"title",placeholder:"Application name","onUpdate:modelValue":o[0]||(o[0]=n=>a.value.title=n),disabled:""},null,512),[[h,a.value.title]])]),e("div",Ht,[zt,r(e("input",{type:"text",class:"form-control",id:"sc-summary",name:"summary",placeholder:"Application summary","onUpdate:modelValue":o[1]||(o[1]=n=>a.value.summary=n)},null,512),[[h,a.value.summary]])]),e("div",Yt,[jt,r(e("select",{class:"form-select",id:"sc-category","onUpdate:modelValue":o[2]||(o[2]=n=>a.value.category=n)},Gt,512),[[w,a.value.category]])]),e("div",Jt,[Qt,r(e("textarea",{class:"form-control",rows:"5",id:"sc-desc","onUpdate:modelValue":o[3]||(o[3]=n=>a.value.description=n)},null,512),[[h,a.value.description]])]),Xt,e("div",Zt,[e("div",ea,[e("div",ta,[aa,e("div",la,[e("div",oa,[e("div",sa,[e("div",null,[na,r(e("input",{type:"number",class:"form-control w-90-per",placeholder:"2","onUpdate:modelValue":o[4]||(o[4]=n=>a.value.recommendedCpu=n)},null,512),[[h,a.value.recommendedCpu]])]),e("div",null,[ia,r(e("input",{type:"number",class:"form-control w-90-per",placeholder:"4","onUpdate:modelValue":o[5]||(o[5]=n=>a.value.recommendedMemory=n)},null,512),[[h,a.value.recommendedMemory]])]),e("div",null,[ca,r(e("input",{type:"number",class:"form-control w-90-per",placeholder:"20","onUpdate:modelValue":o[6]||(o[6]=n=>a.value.recommendedDisk=n)},null,512),[[h,a.value.recommendedDisk]])])])])])]),e("div",da,[ra,e("div",ua,[e("div",ma,[e("div",pa,[e("div",null,[va,r(e("input",{type:"number",class:"form-control w-90-per",placeholder:"2","onUpdate:modelValue":o[7]||(o[7]=n=>a.value.minCpu=n)},null,512),[[h,a.value.minCpu]])]),e("div",null,[ha,r(e("input",{type:"number",class:"form-control w-90-per",placeholder:"4","onUpdate:modelValue":o[8]||(o[8]=n=>a.value.minMemory=n)},null,512),[[h,a.value.minMemory]])]),e("div",null,[ba,r(e("input",{type:"number",class:"form-control w-90-per",placeholder:"20","onUpdate:modelValue":o[9]||(o[9]=n=>a.value.minDisk=n)},null,512),[[h,a.value.minDisk]])])])])])]),e("div",fa,[_a,e("div",ga,[e("div",ya,[e("div",null,[ka,e("div",wa,[r(e("input",{type:"number",class:"form-control w-80-per",placeholder:"8080","onUpdate:modelValue":o[10]||(o[10]=n=>a.value.defaultPort=n)},null,512),[[h,a.value.defaultPort]]),e("div",Sa,[e("button",{class:"btn btn-primary",disabled:"",onClick:q},[z(Y(ee),{class:"icon icon-tabler icon-tabler-plus m-0",size:"24"})]),e("button",{class:"btn btn-primary",disabled:"",onClick:o[11]||(o[11]=n=>M(0))},[z(Y(le),{class:"icon icon-tabler icon-tabler-plus m-0",size:"24"})])])])])])])]),e("div",Ia,[e("h2",$a,[e("button",Ca,[W(" HPA (For K8S) "),r(e("input",{class:"form-check-input ms-1 mt-1",type:"checkbox","onUpdate:modelValue":o[12]||(o[12]=n=>b.value=n),disabled:S.value==="update"},null,8,Ua),[[he,b.value]])])]),e("div",Va,[e("div",Ma,[e("div",xa,[e("div",null,[Ra,r(e("input",{type:"number",class:"form-control w-90-per",placeholder:"1","onUpdate:modelValue":o[13]||(o[13]=n=>a.value.minReplicas=n),disabled:!b.value},null,8,Aa),[[h,a.value.minReplicas]])]),e("div",null,[Na,r(e("input",{type:"number",class:"form-control w-90-per",placeholder:"10","onUpdate:modelValue":o[14]||(o[14]=n=>a.value.maxReplicas=n),disabled:!b.value},null,8,Da),[[h,a.value.maxReplicas]])]),e("div",null,[Ta,r(e("input",{type:"number",class:"form-control w-80-per d-inline",placeholder:"60","onUpdate:modelValue":o[15]||(o[15]=n=>a.value.cpuThreshold=n),disabled:!b.value},null,8,Ea),[[h,a.value.cpuThreshold]]),W(" % ")]),e("div",null,[Pa,r(e("input",{type:"number",class:"form-control w-80-per d-inline",placeholder:"80","onUpdate:modelValue":o[16]||(o[16]=n=>a.value.memoryThreshold=n),disabled:!b.value},null,8,La),[[h,a.value.memoryThreshold]]),W(" % ")])])])])])])]),(c(!0),i(C,null,T(u.value,(n,P)=>(c(),i("div",{class:"row",id:"sc-ref",key:P},[e("div",Fa,[e("div",Ka,[Oa,r(e("select",{class:"form-select",id:"sc-reference-1","onUpdate:modelValue":k=>n.refType=k},Ha,8,qa),[[w,n.refType]])])]),e("div",za,[e("div",Ya,[ja,r(e("input",{type:"text",class:"form-control",id:"sc-ref-value-1",name:"refValue",placeholder:"Ref value","onUpdate:modelValue":k=>n.refValue=k},null,8,Wa),[[h,n.refValue]])])]),e("div",Ga,[e("div",Ja,[r(e("input",{type:"text",class:"form-control w-80-per",id:"sc-ref-desc-1",name:"refDescription",placeholder:"Ref Description","onUpdate:modelValue":k=>n.refDesc=k},null,8,Qa),[[h,n.refDesc]]),e("div",Xa,[e("button",{class:"btn btn-primary",onClick:B},[z(Y(ee),{class:"icon icon-tabler icon-tabler-plus",size:"24",style:{margin:"0px !important"}})]),e("button",{class:"btn btn-primary",onClick:k=>J(P)},[z(Y(le),{class:"icon icon-tabler icon-tabler-plus",size:"24",style:{margin:"0px !important"}})],8,Za)])])])]))),128))]),e("div",el,[e("a",{class:"btn btn-link link-secondary","data-bs-dismiss":"modal",onClick:y}," Cancel "),e("a",{class:"btn btn-primary ms-auto","data-bs-dismiss":"modal",onClick:Q},[z(Y(ee),{class:"icon icon-tabler icon-tabler-plus",size:"24","stroke-width":"2"}),S.value==="new"?(c(),i("span",tl,"Create New Software catalog")):S.value==="update"?(c(),i("span",al,"Update Software catalog")):D("",!0)])])])])]))}}),hl=re(ll,[["__scopeId","data-v-54c32f26"]]);export{vl as A,dl as I,hl as S,Ce as a,ke as b,ul as c,we as d,ml as e,pl as g,Ie as r,rl as s};
