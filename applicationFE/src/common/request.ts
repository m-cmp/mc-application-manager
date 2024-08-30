import axios from "axios";
import { useToast } from "vue-toastification";

const splitUrl = window.location.host.split(':');
const baseUrl = window.location.protocol + '//' + splitUrl[0] + ':18083'
const toast = useToast();
const service = axios.create({
    // baseURL: process.env.VUE_APP_API_URL,
    baseURL: baseUrl,
    timeout: 300000
});


// request interceptor
service.interceptors.request.use(
    config => {
        // console.log("##[", "api", "]##", "request", config.url, config);
        return config;
    },
    error => {
        console.log("error ---------- ", error);
        return Promise.reject(error);
    }
);

// response interceptor
service.interceptors.response.use(
    response => {
        const res = response.data;

        if (res.code === 200) {
            return res;
        } else {
            toast.error(res.detail)
            return Promise.reject(new Error(res.message || "Error"));
        }
    },
    error => {
        console.log("ApiService.Response -> fail", error);

        if (axios.isCancel(error)) {
            return Promise.reject(error);
        }
        // toast.error(error.message)
        return Promise.reject(error);
    }
);

export default service;
