export const getApiBaseUrl = (configuredBaseUrl?: string) => {
  const trimmedBaseUrl = configuredBaseUrl?.trim()

  if (!trimmedBaseUrl) {
    return window.location.origin
  }

  try {
    const configuredUrl = new URL(trimmedBaseUrl)
    const localHostnames = new Set(['localhost', '127.0.0.1', '::1'])

    if (localHostnames.has(configuredUrl.hostname) && !localHostnames.has(window.location.hostname)) {
      return window.location.origin
    }
  } catch {
    return trimmedBaseUrl
  }

  if (trimmedBaseUrl?.startsWith('http://') && window.location.protocol === 'https:') {
    return window.location.origin
  }

  return trimmedBaseUrl
}

export const toAbsoluteUrl = (url?: string | null) => {
  if (!url) {
    return ''
  }

  if (/^(?:[a-z][a-z\d+\-.]*:)?\/\//i.test(url) || url.startsWith('data:')) {
    return url
  }

  return `${window.location.origin}${url.startsWith('/') ? '' : '/'}${url}`
}
