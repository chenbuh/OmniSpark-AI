import request from './request'

export interface CaptchaData {
  captchaId: string
  type: 'rotate' | 'sequence' | 'track'
  width: number
  height: number
  image: string
  prompt: string
  thumb?: string | null
  sequenceLabels?: string[] | null
  trackPath?: number[][] | null
  trackY?: number | null
}

export interface CaptchaVerifyPayload {
  captchaId: string
  angle?: number
  points?: number[][]
  x?: number
  trail: number[][]
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown): unknown {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error('验证码数据待确认')
  }
  return response.data
}

function normalizeNumber(value: unknown, errorMessage: string) {
  const normalized = Number(value)
  if (!Number.isFinite(normalized)) {
    throw new Error(errorMessage)
  }
  return normalized
}

function normalizeOptionalStringArray(value: unknown, errorMessage: string) {
  if (value == null) {
    return null
  }
  if (!Array.isArray(value)) {
    throw new Error(errorMessage)
  }
  return value.map((item) => {
    if (typeof item !== 'string') {
      throw new Error(errorMessage)
    }
    return item
  })
}

function normalizePointMatrix(value: unknown, errorMessage: string) {
  if (value == null) {
    return null
  }
  if (!Array.isArray(value)) {
    throw new Error(errorMessage)
  }
  return value.map((point) => {
    if (!Array.isArray(point) || point.length !== 2) {
      throw new Error(errorMessage)
    }
    return [
      normalizeNumber(point[0], errorMessage),
      normalizeNumber(point[1], errorMessage)
    ]
  })
}

function requireCaptchaData(value: unknown): CaptchaData {
  if (!isPlainObject(value)) {
    throw new Error('验证码数据待确认')
  }
  const captchaId = typeof value.captchaId === 'string' ? value.captchaId.trim() : ''
  const type = value.type
  const image = typeof value.image === 'string' ? value.image.trim() : ''
  const prompt = typeof value.prompt === 'string' ? value.prompt.trim() : ''
  const thumb = value.thumb == null ? null : typeof value.thumb === 'string' ? value.thumb : null
  const trackY = value.trackY == null ? null : normalizeNumber(value.trackY, '验证码数据待确认')
  if (!captchaId || (type !== 'rotate' && type !== 'sequence' && type !== 'track') || !image || !prompt) {
    throw new Error('验证码数据待确认')
  }
  return {
    captchaId,
    type,
    width: normalizeNumber(value.width, '验证码数据待确认'),
    height: normalizeNumber(value.height, '验证码数据待确认'),
    image,
    prompt,
    thumb,
    sequenceLabels: normalizeOptionalStringArray(value.sequenceLabels, '验证码数据待确认'),
    trackPath: normalizePointMatrix(value.trackPath, '验证码数据待确认'),
    trackY
  }
}

function requireCaptchaTicket(value: unknown) {
  if (!isPlainObject(value)) {
    throw new Error('验证码票据待确认')
  }
  const ticket = typeof value.ticket === 'string' ? value.ticket.trim() : ''
  if (!ticket) {
    throw new Error('验证码票据待确认')
  }
  return ticket
}

export const captchaApi = {
  // 获取一个随机形态的验证码（答案在后端，前端拿不到）
  async generate() {
    const response = await request.get<unknown>('/api/auth/captcha/generate', {
      params: { _: Date.now() },
      headers: {
        'x-no-cache': '1',
        'Cache-Control': 'no-cache',
        Pragma: 'no-cache'
      }
    })
    return requireCaptchaData(getResponseData(response))
  },

  // 提交作答，通过则返回一次性票据
  async verify(payload: CaptchaVerifyPayload) {
    const response = await request.post<unknown>('/api/auth/captcha/verify', payload)
    return requireCaptchaTicket(getResponseData(response))
  }
}
