import{u as n,H as t}from"./index-DepoFQb4.js";const i=window.location.host.split(":"),l=window.location.protocol+"//"+i[0]+":18084",o=n(),r=t.create({baseURL:l,timeout:3e5});r.interceptors.request.use(e=>(console.log("##[","api","]##","request",e.url,e),e),e=>(console.log("error ---------- ",e),Promise.reject(e)));r.interceptors.response.use(e=>{const s=e.data;return s.code===200?s:(o.error(s.detail),Promise.reject(new Error(s.message||"Error")))},e=>{console.log("ApiService.Response -> fail",e);const s=e.response;return console.log(e.response),s.status===404&&o.error("API Call Fail :: Code 404"),t.isCancel(e),Promise.reject(e)});export{r as s};
