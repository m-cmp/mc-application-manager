import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import '@tabler/core/dist/css/tabler.min.css'
import 'tabulator-tables/dist/css/tabulator_bootstrap4.min.css'
import axios from 'axios'
import './permission'

const app = createApp(App)

// Axios
axios.defaults.baseURL = import.meta.env.VITE_API_URL
app.config.globalProperties.axios = axios

app.use(createPinia())

import router from './router'
app.use(router)

import Toast from "vue-toastification";
import "vue-toastification/dist/index.css";
app.use(Toast, {});


import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap/dist/js/bootstrap.bundle.min.js'

app.mount('#app')

