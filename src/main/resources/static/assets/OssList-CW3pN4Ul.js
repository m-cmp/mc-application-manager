import{_ as q}from"./TableHeader.vue_vue_type_script_setup_true_lang-DGIiBpgi.js";import{_ as R}from"./Tabulator.vue_vue_type_style_index_0_lang-BYbrq1sD.js";import{s as v}from"./request-DbsCHHUH.js";import{d as D,u as N,c as A,w as I,o as T,r as p,a as u,b as s,t as k,e as g,v as M,F as G,f as W,g as O,h as m,i as S}from"./index-BXCHGTTi.js";const j=()=>v.get("/ossType/list"),z=()=>v.get("/ossType/filter/list"),H=()=>v.get("/oss/list");function J(o){return v.get(`/oss/duplicate?ossName=${o.ossName}&ossUrl=${o.ossUrl}&ossUsername=${o.ossUsername}`)}function K(o){return v.post("/oss/connection-check",o)}function Q(o){return v.get("/oss/"+o)}function X(o){return v.post("/oss",o)}function Y(o){return v.patch(`/oss/${o.ossIdx}`,o)}function Z(o){return v.delete(`/oss/${o}`)}const ss={class:"modal",id:"ossForm",tabindex:"-1"},es={class:"modal-dialog modal-xl",role:"document"},ts={class:"modal-content"},os=s("button",{type:"button",class:"btn-close","data-bs-dismiss":"modal","aria-label":"Close"},null,-1),as={class:"modal-body text-left py-4"},ls={class:"mb-5"},ns={class:"mb-3"},cs=s("label",{class:"form-label required"},"OSS Type",-1),is={class:"grid gap-0 column-gap-3"},ds=s("option",{value:0},"Select OSS Type",-1),rs=["value"],us={class:"row mb-3"},ms=s("label",{class:"form-label required"},"OSS Name",-1),ps={class:"grid gap-0 column-gap-3"},vs={class:"mb-3"},_s=s("label",{class:"form-label required"},"OSS Description",-1),bs={class:"mb-3"},hs=s("label",{class:"form-label required"},"URL",-1),fs={class:"row"},gs={class:"col"},ys=s("label",{class:"form-label required"},"OSS ID",-1),ws={class:"col"},Os=s("label",{class:"form-label required"},"OSS PW",-1),Ss={class:"col mt-4 row"},ks={key:1,class:"btn btn-success col",style:{"margin-right":"3px"}},xs={key:3,class:"btn btn-success col"},Cs={class:"modal-footer"},Us=D({__name:"ossForm",props:{mode:{},ossIdx:{}},emits:["get-oss-list"],setup(o,{emit:y}){const n=N(),c=o,_=y,b=A(()=>c.ossIdx);I(b,async()=>{await r()}),I(()=>c.mode,async()=>{await f(c.mode)}),T(async()=>{await f("init"),await r()});const e=p({}),r=async()=>{if(c.mode==="new")e.value.ossTypeIdx=0,e.value.ossName="",e.value.ossDesc="",e.value.ossUrl="",e.value.ossUsername="",e.value.ossPassword="",i.value=!1,d.value=!1;else{const{data:a}=await Q(c.ossIdx);e.value=a,e.value.ossPassword=V(e.value.ossPassword),i.value=!0,d.value=!0}},h=p([]),f=async a=>{try{if(a==="new"||a==="init"){const{data:t}=await z();h.value=t}else{const{data:t}=await j();h.value=t}}catch(t){console.log(t)}},x=()=>{e.value.ossPassword="",d.value=!1},i=p(!1),w=async()=>{const a={ossName:e.value.ossName,ossUrl:e.value.ossUrl,ossUsername:e.value.ossUsername},{data:t}=await J(a);t?n.error("이미 사용중인 이름입니다."):(n.success("사용 가능한 이름입니다."),i.value=!0)},d=p(!1),C=async()=>{const a={ossUrl:e.value.ossUrl,ossUsername:e.value.ossUsername,ossPassword:$(e.value.ossPassword),ossTypeIdx:e.value.ossTypeIdx},{data:t}=await K(a);t?(n.success("사용 가능한 OSS입니다."),d.value=!0):n.error("사용 불가능한 OSS입니다.")},L=()=>{i.value=!1},U=()=>{d.value=!1},P=async()=>{e.value.ossPassword=$(e.value.ossPassword),c.mode==="new"?await F().then(()=>{_("get-oss-list")}):await E().then(()=>{_("get-oss-list")}),r()},F=async()=>{const{data:a}=await X(e.value);a?n.success("등록되었습니다."):n.error("등록 할 수 없습니다.")},E=async()=>{const{data:a}=await Y(e.value);a?n.success("등록되었습니다."):n.error("등록 할 수 없습니다.")},$=a=>btoa(a),V=a=>atob(a);return(a,t)=>(m(),u("div",ss,[s("div",es,[s("div",ts,[os,s("div",as,[s("h3",ls,k(c.mode==="new"?"New":"Edit")+" OSS ",1),s("div",null,[s("div",ns,[cs,s("div",is,[g(s("select",{"onUpdate:modelValue":t[0]||(t[0]=l=>e.value.ossTypeIdx=l),class:"form-select p-2 g-col-12"},[ds,(m(!0),u(G,null,W(h.value,(l,B)=>(m(),u("option",{value:l.ossTypeIdx,key:B},k(l.ossTypeName),9,rs))),128))],512),[[M,e.value.ossTypeIdx]])])]),s("div",us,[ms,s("div",ps,[g(s("input",{type:"text",class:"form-control p-2 g-col-11",placeholder:"Enter the OSS Name","onUpdate:modelValue":t[1]||(t[1]=l=>e.value.ossName=l),onChange:L},null,544),[[O,e.value.ossName]])])]),s("div",vs,[_s,g(s("input",{type:"text",class:"form-control p-2 g-col-11",placeholder:"Enter the OSS Description","onUpdate:modelValue":t[2]||(t[2]=l=>e.value.ossDesc=l)},null,512),[[O,e.value.ossDesc]])]),s("div",bs,[hs,g(s("input",{type:"text",class:"form-control p-2 g-col-7",placeholder:"Enter the Server URL","onUpdate:modelValue":t[3]||(t[3]=l=>e.value.ossUrl=l),onFocus:U},null,544),[[O,e.value.ossUrl]])]),s("div",fs,[s("div",gs,[ys,g(s("input",{type:"text",class:"form-control p-2 g-col-7",placeholder:"Enter the OSS ID","onUpdate:modelValue":t[4]||(t[4]=l=>e.value.ossUsername=l),onFocus:U},null,544),[[O,e.value.ossUsername]])]),s("div",ws,[Os,g(s("input",{type:"password",class:"form-control p-2 g-col-11",placeholder:"Enter the OSS Password","onUpdate:modelValue":t[5]||(t[5]=l=>e.value.ossPassword=l),onClick:x,onFocus:U},null,544),[[O,e.value.ossPassword]])]),s("div",Ss,[i.value?(m(),u("button",ks,"Duplicate Check")):(m(),u("button",{key:0,class:"btn btn-primary col",onClick:w,style:{"margin-right":"3px"}},"Duplicate Check")),d.value?(m(),u("button",xs,"Connection Check")):(m(),u("button",{key:2,class:"btn btn-primary col",onClick:C},"Connection Check"))])])])]),s("div",Cs,[s("a",{href:"#",class:"btn btn-link link-secondary","data-bs-dismiss":"modal",onClick:t[6]||(t[6]=l=>r())}," Cancel "),s("a",{href:"#",class:"btn btn-primary ms-auto","data-bs-dismiss":"modal",onClick:t[7]||(t[7]=l=>P())},k(c.mode==="new"?"Regist":"Edit"),1)])])])]))}}),Ds={class:"modal",id:"deleteOss",tabindex:"-1"},Ns={class:"modal-dialog modal-lg",role:"document"},$s={class:"modal-content"},Is=s("button",{type:"button",class:"btn-close","data-bs-dismiss":"modal","aria-label":"Close"},null,-1),Ts=s("div",{class:"modal-status bg-danger"},null,-1),Ls={class:"modal-body text-left py-4"},Ps=s("h3",{class:"mb-5"}," Delete OSS ",-1),Fs={class:"modal-footer"},Es=s("a",{href:"#",class:"btn btn-link link-secondary","data-bs-dismiss":"modal"}," Cancel ",-1),Vs=D({__name:"deleteOss",props:{ossName:{},ossIdx:{}},emits:["get-oss-list"],setup(o,{emit:y}){const n=N(),c=o,_=y,b=async()=>{const{data:e}=await Z(c.ossIdx);e?n.success("삭제되었습니다."):n.error("삭제하지 못했습니다."),_("get-oss-list")};return(e,r)=>(m(),u("div",Ds,[s("div",Ns,[s("div",$s,[Is,Ts,s("div",Ls,[Ps,s("h4",null,"Are you sure you want to delete "+k(c.ossName)+"?",1)]),s("div",Fs,[Es,s("a",{href:"#",class:"btn btn-primary ms-auto","data-bs-dismiss":"modal",onClick:r[0]||(r[0]=h=>b())}," Delete ")])])])]))}}),Bs={class:"card card-flush w-100"},Gs=D({__name:"OssList",setup(o){const y=N(),n=p([]),c=p([]);T(async()=>{r(),await _()});const _=async()=>{try{const{data:i}=await H();n.value=i}catch(i){console.log(i),y.error("데이터를 가져올 수 없습니다.")}},b=p(0),e=p(""),r=()=>{c.value=[{title:"OSS Name",field:"ossName",width:400},{title:"OSS Desc",field:"ossDesc",width:500},{title:"URL",field:"ossUrl",width:600},{title:"Action",width:400,formatter:h,cellClick:function(i,w){const d=i.target,C=d==null?void 0:d.getAttribute("id");b.value=w.getRow().getData().ossIdx,C==="edit-btn"?f.value="edit":e.value=w.getRow().getData().ossName}}]},h=()=>`
  <div>
    <button
      class='btn btn-primary d-none d-sm-inline-block mr-5'
      id='edit-btn'
      data-bs-toggle='modal'
      data-bs-target='#ossForm'>
      EDIT
    </button>
    <button
      class='btn btn-danger d-none d-sm-inline-block'
      id='delete-btn'
      data-bs-toggle='modal'
      data-bs-target='#deleteOss'>
      DELETE
    </button>
  </div>`,f=p("new"),x=()=>{b.value=0,f.value="new"};return(i,w)=>(m(),u("div",Bs,[S(q,{"header-title":"OSS","new-btn-title":"New OSS","popup-flag":!0,"popup-target":"#ossForm",onClickNewBtn:x}),S(R,{columns:c.value,"table-data":n.value},null,8,["columns","table-data"]),S(Us,{mode:f.value,"oss-idx":b.value,onGetOssList:_},null,8,["mode","oss-idx"]),S(Vs,{"oss-name":e.value,"oss-idx":b.value,onGetOssList:_},null,8,["oss-name","oss-idx"])]))}});export{Gs as default};