type RiskCaptchaHandler = () => Promise<string>

let riskCaptchaHandler: RiskCaptchaHandler | null = null
let inFlightCaptchaPromise: Promise<string> | null = null

export function registerRiskCaptchaHandler(handler: RiskCaptchaHandler) {
  riskCaptchaHandler = handler
}

export function unregisterRiskCaptchaHandler(handler?: RiskCaptchaHandler) {
  if (!handler || riskCaptchaHandler === handler) {
    riskCaptchaHandler = null
  }
}

export async function requestRiskCaptchaTicket(): Promise<string> {
  if (!riskCaptchaHandler) {
    throw new Error('系统要求完成二次验证，但验证面板尚未就绪，请刷新页面后重试')
  }

  if (!inFlightCaptchaPromise) {
    inFlightCaptchaPromise = riskCaptchaHandler().finally(() => {
      inFlightCaptchaPromise = null
    })
  }

  return inFlightCaptchaPromise
}
