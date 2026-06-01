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

export const captchaApi = {
  // 获取一个随机形态的验证码（答案在后端，前端拿不到）
  async generate() {
    const res = await request.get('/api/auth/captcha/generate', {
      params: { _: Date.now() },
      headers: {
        'x-no-cache': '1',
        'Cache-Control': 'no-cache',
        Pragma: 'no-cache'
      }
    })
    return res.data as CaptchaData
  },

  // 提交作答，通过则返回一次性票据
  async verify(payload: CaptchaVerifyPayload) {
    const res = await request.post('/api/auth/captcha/verify', payload)
    return (res.data as { ticket: string }).ticket
  }
}
