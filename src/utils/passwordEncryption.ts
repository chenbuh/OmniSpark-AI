import request from '@/api/request'

interface PasswordPublicKeyPayload {
  algorithm: string
  publicKey: string
}

function pemToArrayBuffer(pem: string) {
  const base64 = pem
    .replace('-----BEGIN PUBLIC KEY-----', '')
    .replace('-----END PUBLIC KEY-----', '')
    .replace(/\s+/g, '')
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

async function fetchPasswordPublicKey() {
  const res = await request.get('/api/auth/public-key', {
    headers: { 'x-no-cache': '1' }
  })
  return res.data as PasswordPublicKeyPayload
}

export async function encryptPassword(plainPassword: string) {
  if (!plainPassword) {
    return ''
  }
  if (!window.crypto?.subtle) {
    throw new Error('当前浏览器不支持密码加密，请升级浏览器后重试')
  }

  const { publicKey } = await fetchPasswordPublicKey()
  const importedKey = await window.crypto.subtle.importKey(
    'spki',
    pemToArrayBuffer(publicKey),
    {
      name: 'RSA-OAEP',
      hash: 'SHA-256'
    },
    false,
    ['encrypt']
  )

  const encrypted = await window.crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    importedKey,
    new TextEncoder().encode(plainPassword)
  )

  const bytes = new Uint8Array(encrypted)
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary)
}
