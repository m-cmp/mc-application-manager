import{d as h,h as s,a as o,b as t,t as a,q as i,i as c,s as l,x as r}from"./index-DepoFQb4.js";import{I as p}from"./IconPlus-DP-oamn8.js";const m={class:"page-header page-wrapper"},w={class:"row align-items-center"},g={class:"card-header d-flex",style:{"justify-content":"space-between"}},k={class:"card-title"},u={class:"btn-list"},T=["data-bs-target"],x=h({__name:"TableHeader",props:{headerTitle:{},newBtnTitle:{},popupFlag:{type:Boolean},popupTarget:{}},emits:["click-new-btn"],setup(d,{emit:b}){const e=d,_=b,n=()=>{_("click-new-btn")};return(f,y)=>(s(),o("div",m,[t("div",w,[t("div",g,[t("h3",k,[t("strong",null,a(e.headerTitle),1)]),t("div",u,[e.popupFlag?(s(),o("a",{key:1,class:"btn btn-primary d-none d-sm-inline-block","data-bs-toggle":"modal","data-bs-target":e.popupTarget,onClick:i(n,["prevent","stop"])},[c(l(p),{class:"icon icon-tabler icon-tabler-plus",color:"white",size:20,"stroke-width":"1"}),r(" "+a(e.newBtnTitle),1)],8,T)):(s(),o("a",{key:0,class:"btn btn-primary d-none d-sm-inline-block",onClick:i(n,["prevent","stop"])},[c(l(p),{class:"icon icon-tabler icon-tabler-plus",color:"white",size:20,"stroke-width":"1"}),r(" "+a(e.newBtnTitle),1)]))])])])]))}});export{x as _};
