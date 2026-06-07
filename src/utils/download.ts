import { API_BASE_URL } from '@/api/request'

export async function downloadUrl(url: string, fileName?: string) {
  if (!url) {
    throw new Error('下载地址为空')
  }
  const requestUrl = resolveDownloadUrl(url)
  const token = localStorage.getItem('satoken')
  const response = await fetch(requestUrl, {
    credentials: 'include',
    headers: token ? { satoken: token } : undefined
  })
  if (!response.ok) {
    throw new Error(`下载失败 (${response.status})`)
  }
  const blob = await response.blob()
  if (blob.size <= 0) {
    throw new Error('下载文件为空')
  }
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = normalizeDownloadFileName(fileName, response)
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}

function resolveDownloadUrl(url: string) {
  if (/^https?:\/\//i.test(url)) {
    return url
  }
  return new URL(url, `${API_BASE_URL}/`).toString()
}

function normalizeDownloadFileName(fileName: string | undefined, response: Response) {
  const normalized = fileName?.trim()
  if (normalized) {
    return normalized
  }
  const disposition = response.headers.get('content-disposition') || ''
  const match = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(disposition)
  const encodedName = match?.[1] || match?.[2]
  if (encodedName) {
    try {
      return decodeURIComponent(encodedName)
    } catch {
      return encodedName
    }
  }
  return 'download'
}
