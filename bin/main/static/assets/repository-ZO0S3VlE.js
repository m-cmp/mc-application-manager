import{s}from"./request-DDEURQ0f.js";const n=e=>s.get(`/oss/v1/repositories/${e}/list`);function i(e,t){return s.delete(`/oss/v1/repositories/${e}/delete/${t}`)}function p(e,t){return s.post(`/oss/v1/repositories/${e}/create`,t)}const a=(e,t)=>s.get(`/oss/v1/repositories/${e}/detail/${t}`),u=(e,t)=>s.put(`/oss/v1/repositories/${e}/update`,t);function c(e,t){return s.delete(`/oss/v1/components/${e}/delete/${t}`)}const $=(e,t)=>s.get(`/oss/v1/components/${e}/list/${t}`),d=(e,t,o)=>s.post(`/oss/v1/components/${e}/create/${t}`,o);export{$ as a,u as b,i as c,c as d,n as e,a as g,p as r,d as u};
