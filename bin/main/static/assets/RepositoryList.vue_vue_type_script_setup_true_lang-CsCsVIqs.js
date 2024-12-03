import{_ as U}from"./TableHeader.vue_vue_type_script_setup_true_lang-DulTAMyI.js";import{_ as D}from"./Tabulator.vue_vue_type_style_index_0_lang-AyImqkUy.js";import{g as P,r as F,b as q,c as L,e as B}from"./repository-Cc5uirx3.js";import{d as x,u as $,c as A,w as M,o as V,r as _,a as C,b as e,t as R,e as r,g as w,z as p,h as N,A as S,i as k}from"./index-BHIrf46W.js";const T={class:"modal",id:"repositoryForm",tabindex:"-1"},E={class:"modal-dialog modal-lg",role:"document"},G={class:"modal-content"},H=e("button",{type:"button",class:"btn-close","data-bs-dismiss":"modal","aria-label":"Close"},null,-1),I={class:"modal-body text-left py-4"},O={class:"mb-5"},z={class:"row mb-3"},j=e("label",{class:"form-label required"},"Name",-1),J={class:"grid gap-0 column-gap-3"},K=["disabled"],Q={class:"mb-3"},W=e("label",{class:"form-label required"},"Format",-1),X={class:"form-check form-check-inline"},Y=["disabled"],Z=e("span",{class:"form-check-label"},"raw",-1),ee={class:"form-check form-check-inline"},te=["disabled"],oe=e("span",{class:"form-check-label"},"helm",-1),se={class:"form-check form-check-inline"},ae=["disabled"],le=e("span",{class:"form-check-label"},"docker",-1),ne={class:"mb-3"},ie=e("label",{class:"form-label required"},"Allow",-1),re={class:"form-check form-check-inline"},de=e("span",{class:"form-check-label"},"allow",-1),ce={class:"form-check form-check-inline"},me=e("span",{class:"form-check-label"},"allow_once",-1),ue={class:"form-check form-check-inline"},pe=e("span",{class:"form-check-label"},"deny",-1),_e={class:"mb-3"},be=e("label",{class:"form-label required"},"On/Offline",-1),ve={class:"form-check form-check-inline"},fe=e("span",{class:"form-check-label"},"true",-1),he={class:"form-check form-check-inline"},ye=e("span",{class:"form-check-label"},"false",-1),ke=e("div",{class:"mb-3"},[e("label",{class:"form-label required"},"Storage"),e("div",{class:"grid gap-0 column-gap-3"},[e("input",{type:"text",class:"form-control p-2 g-col-11",value:"defalut",disabled:""})])],-1),ge={class:"mb-3"},we=e("label",{class:"form-label required"},"Http",-1),Re={class:"grid gap-0 column-gap-3"},xe=["disabled"],$e={class:"mb-3"},Ce=e("label",{class:"form-label required"},"Https",-1),Ne={class:"grid gap-0 column-gap-3"},Ve=["disabled"],Ue={class:"modal-footer"},De=x({__name:"repositoryForm",props:{mode:{},repositoryName:{}},emits:["get-repository-list"],setup(y,{emit:f}){const d=$(),a=y,u=f,i=A(()=>a.repositoryName);M(i,async()=>{await n()}),V(async()=>{await n()});const t=_({}),l=_(""),c=_(0),b=_(0),n=async()=>{if(a.mode==="new")t.value.name="",t.value.format="raw",t.value.type="hosted",t.value.url="",t.value.online=!0,c.value=0,b.value=0,l.value="allow";else{const{data:m}=await P("nexus",a.repositoryName);t.value=m,l.value=m.storage.writePolicy,m.format=="docker"&&(c.value=m.docker.httpPort,b.value=m.docker.httpsPort)}},v=async()=>{t.value.storage={blobStoreName:"default",strictContentTypeValidation:!0,writePolicy:l.value},t.value.format!="docker"?t.value.docker=null:t.value.docker={v1Enabled:!0,forceBasicAuth:!0,httpPort:c.value,httpsPort:b.value,subdomain:"/test"},a.mode==="new"?await h().then(()=>{u("get-repository-list"),n()}):await g().then(()=>{u("get-repository-list"),n()})},h=async()=>{const{data:m}=await F("nexus",t.value);m?d.success("등록되었습니다."):d.error("등록 할 수 없습니다.")},g=async()=>{const{data:m}=await q("nexus",t.value);m?d.success("등록되었습니다."):d.error("등록 할 수 없습니다.")};return(m,o)=>(N(),C("div",T,[e("div",E,[e("div",G,[H,e("div",I,[e("h3",O," Repository "+R(a.mode==="new"?"생성":"수정"),1),e("div",null,[e("div",z,[j,e("div",J,[r(e("input",{type:"text",class:"form-control p-2 g-col-11","onUpdate:modelValue":o[0]||(o[0]=s=>t.value.name=s),disabled:a.mode!="new"},null,8,K),[[w,t.value.name]])])]),e("div",Q,[W,e("div",null,[e("label",X,[r(e("input",{class:"form-check-input",type:"radio",name:"format",value:"raw","onUpdate:modelValue":o[1]||(o[1]=s=>t.value.format=s),disabled:a.mode!="new"},null,8,Y),[[p,t.value.format]]),Z]),e("label",ee,[r(e("input",{class:"form-check-input",type:"radio",name:"format",value:"helm","onUpdate:modelValue":o[2]||(o[2]=s=>t.value.format=s),disabled:a.mode!="new"},null,8,te),[[p,t.value.format]]),oe]),e("label",se,[r(e("input",{class:"form-check-input",type:"radio",name:"format",value:"docker","onUpdate:modelValue":o[3]||(o[3]=s=>t.value.format=s),disabled:a.mode!="new"},null,8,ae),[[p,t.value.format]]),le])])]),e("div",ne,[ie,e("div",null,[e("label",re,[r(e("input",{class:"form-check-input",type:"radio",name:"allow",value:"allow","onUpdate:modelValue":o[4]||(o[4]=s=>l.value=s)},null,512),[[p,l.value]]),de]),e("label",ce,[r(e("input",{class:"form-check-input",type:"radio",name:"allow",value:"allow_once","onUpdate:modelValue":o[5]||(o[5]=s=>l.value=s)},null,512),[[p,l.value]]),me]),e("label",ue,[r(e("input",{class:"form-check-input",type:"radio",name:"allow",value:"deny","onUpdate:modelValue":o[6]||(o[6]=s=>l.value=s)},null,512),[[p,l.value]]),pe])])]),e("div",_e,[be,e("div",null,[e("label",ve,[r(e("input",{class:"form-check-input",type:"radio",name:"online",value:"true","onUpdate:modelValue":o[7]||(o[7]=s=>t.value.online=s)},null,512),[[p,t.value.online]]),fe]),e("label",he,[r(e("input",{class:"form-check-input",type:"radio",name:"online",value:"false","onUpdate:modelValue":o[8]||(o[8]=s=>t.value.online=s)},null,512),[[p,t.value.online]]),ye])])]),ke,e("div",ge,[we,e("div",Re,[r(e("input",{type:"text",class:"form-control p-2 g-col-11","onUpdate:modelValue":o[9]||(o[9]=s=>c.value=s),disabled:t.value.format!="docker"},null,8,xe),[[w,c.value]])])]),e("div",$e,[Ce,e("div",Ne,[r(e("input",{type:"text",class:"form-control p-2 g-col-11","onUpdate:modelValue":o[10]||(o[10]=s=>b.value=s),disabled:t.value.format!="docker"},null,8,Ve),[[w,b.value]])])])])]),e("div",Ue,[e("a",{href:"#",class:"btn btn-link link-secondary","data-bs-dismiss":"modal",onClick:o[11]||(o[11]=s=>n())}," Cancel "),e("a",{href:"#",class:"btn btn-primary ms-auto","data-bs-dismiss":"modal",onClick:o[12]||(o[12]=s=>v())},R(a.mode==="new"?"생성":"수정"),1)])])])]))}}),Pe={class:"modal",id:"deleteRepository",tabindex:"-1"},Fe={class:"modal-dialog modal-lg",role:"document"},qe={class:"modal-content"},Le=e("button",{type:"button",class:"btn-close","data-bs-dismiss":"modal","aria-label":"Close"},null,-1),Be=e("div",{class:"modal-status bg-danger"},null,-1),Ae={class:"modal-body text-left py-4"},Me=e("h3",{class:"mb-5"}," Repository 삭제 ",-1),Se={class:"modal-footer"},Te=e("a",{href:"#",class:"btn btn-link link-secondary","data-bs-dismiss":"modal"}," Cancel ",-1),Ee=x({__name:"deleteRepository",props:{repositoryName:{}},emits:["get-repository-list"],setup(y,{emit:f}){const d=$(),a=y,u=f,i=async()=>{const{data:t}=await L("nexus",a.repositoryName);t?d.success("삭제되었습니다."):d.error("삭제하지 못했습니다."),u("get-repository-list")};return(t,l)=>(N(),C("div",Pe,[e("div",Fe,[e("div",qe,[Le,Be,e("div",Ae,[Me,e("h4",null,R(a.repositoryName)+"을(를) 정말 삭제하시겠습니까?",1)]),e("div",Se,[Te,e("a",{href:"#",class:"btn btn-primary ms-auto","data-bs-dismiss":"modal",onClick:l[0]||(l[0]=c=>i())}," 삭제 ")])])])]))}}),Ge={class:"card card-flush w-100"},je=x({__name:"RepositoryList",setup(y){const f=$(),d=_([]),a=_([]);V(async()=>{t(),await u()});const u=async()=>{try{const{data:n}=await B("nexus");d.value=n}catch(n){console.log(n),f.error("데이터를 가져올 수 없습니다.")}},i=_(""),t=()=>{a.value=[{title:"Name",field:"name",width:"15%",cellClick:function(n,v){n.stopPropagation(),i.value=v.getRow().getData().name,S.push("/web/repository/detail/"+i.value)}},{title:"Format",field:"format",width:"10%"},{title:"URL",field:"url",width:"40%"},{title:"Type(hosted)",field:"type",width:"15%"},{title:"Action",width:"20%",formatter:l,cellClick:function(n,v){const h=n.target,g=h==null?void 0:h.getAttribute("id");i.value=v.getRow().getData().name,g==="edit-btn"?c.value="edit":i.value=v.getRow().getData().name}}]},l=()=>`
  <div>
    <button
      class='btn btn-primary d-none d-sm-inline-block me-1'
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
  </div>`,c=_("new"),b=()=>{i.value="",c.value="new"};return(n,v)=>(N(),C("div",Ge,[k(U,{"header-title":"Repository","new-btn-title":"New Repository","popup-flag":!0,"popup-target":"#repositoryForm",onClickNewBtn:b}),k(D,{columns:a.value,"table-data":d.value},null,8,["columns","table-data"]),k(De,{mode:c.value,"repository-name":i.value,onGetRepositoryList:u},null,8,["mode","repository-name"]),k(Ee,{"repository-name":i.value,onGetRepositoryList:u},null,8,["repository-name"])]))}});export{je as _};
