import axios from "axios";
import { useToast } from "vue-toastification";
import { getApiBaseUrl } from "@/common/url";
import { useUserStore } from "@/stores/user";

const baseUrl = getApiBaseUrl(import.meta.env.VITE_API_URL)
const toast = useToast();
const service = axios.create({
  // baseURL: process.env.VUE_APP_API_URL,
  baseURL: baseUrl,
  timeout: 300000
});

const normalizeValue = (value: unknown) => {
  const normalized = String(value ?? '').trim()
  return ['undefined', 'null'].includes(normalized.toLowerCase()) ? '' : normalized
}

const getCookieValue = (name: string) => {
  if (typeof document === 'undefined') return ''
  const prefix = `${name}=`
  const cookie = document.cookie
    .split(';')
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix))
  return cookie ? cookie.slice(prefix.length) : ''
}

export const getProjectContextHeaders = (): Record<string, string> => {
  let userStore
  try {
    userStore = useUserStore()
  } catch {
    // API modules can be imported before Pinia is installed during bootstrap.
    return {}
  }

  // mc-admin-cli exposes Web Console and AM on the same browser hostname
  // (usually different ports). Cookies are shared across ports, so this picks
  // up Web Console's proactively refreshed token instead of retaining only
  // the token received when the iframe was first loaded.
  const accessToken = normalizeValue(getCookieValue('Authorization') || userStore.accessToken)
  const workspaceId = normalizeValue(userStore.workspaceInfo?.id ?? userStore.workspaceInfo?.Id)
  const projectId = normalizeValue(userStore.projectInfo?.id ?? userStore.projectInfo?.Id)
  const namespaceId = normalizeValue(userStore.getNsId())

  const headers: Record<string, string> = {}
  if (accessToken) {
    headers.Authorization = accessToken.toLowerCase().startsWith('bearer ')
      ? `Bearer ${accessToken.slice(7).trim()}`
      : `Bearer ${accessToken}`
  }
  if (workspaceId) headers['X-MCMP-Workspace-ID'] = workspaceId
  if (projectId) headers['X-MCMP-Project-ID'] = projectId
  if (namespaceId) headers['X-MCMP-Namespace-ID'] = namespaceId
  return headers
}


// request interceptor
service.interceptors.request.use(
  config => {
    const contextHeaders = getProjectContextHeaders()
    Object.entries(contextHeaders).forEach(([name, value]) => {
      if (typeof config.headers?.set === 'function') {
        config.headers.set(name, value)
      } else {
        ;(config.headers as any)[name] = value
      }
    })
    return config;
  },
  error => {
    console.warn('API request setup failed', error?.message || 'Unknown error');
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
      if (!response.config?.url?.startsWith('/applications/deployment-submissions/')) toast.error(res.detail)
      const error = Object.assign(new Error(res.detail || res.message || "Error"), {
        deploymentId: res.data?.deploymentId
      })
      return Promise.reject(error);
    }
  },
  error => {
    const res = error.response
    console.warn('API request failed', {
      url: error?.config?.url,
      status: res?.status,
      message: error?.message
    })
    if (res?.status === 404 && !error?.config?.url?.startsWith('/applications/deployment-submissions/')) {
      toast.error('API Call Fail :: Code 404')
    }
    if (axios.isCancel(error)) {
      return Promise.reject(error);
    }
    if (res?.data?.data?.deploymentId) {
      error.deploymentId = res.data.data.deploymentId
      error.message = res.data.detail || res.data.message || error.message
    }
    // toast.error(error.message)
    return Promise.reject(error);
  }
);

export default service;
