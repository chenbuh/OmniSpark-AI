<template>
  <div ref="loginRoot" class="login-container">
    <!-- 背景流光装饰 -->
    <div class="gradient-bg">
      <div class="bubble bubble-1"></div>
      <div class="bubble bubble-2"></div>
    </div>

    <!-- 登录/注册卡片 -->
    <n-card class="login-card glass-card" :bordered="false">
      <div class="login-header">
        <div class="logo-box">
          <Zap class="logo-icon" />
        </div>
        <h2>{{ platformName }}</h2>
        <p class="subtitle">{{ isLoginMode ? '一体化高保真生图与视频创作平台' : '加入多维 AI 创意空间' }}</p>
      </div>

      <!-- 1. 管理员二次验证 -->
      <n-form
        v-if="isLoginMode && totpState.stage !== 'none'"
        class="login-form"
      >
        <div class="totp-panel">
          <h3>{{ totpState.stage === 'setup' ? '管理员首次登录验证' : '管理员动态验证码' }}</h3>
          <p class="subtitle">
            {{ totpState.stage === 'setup'
              ? '请使用验证器 App 扫码绑定；如果 App 不支持扫码，也可以复制密钥或绑定链接完成令牌激活。'
              : '请输入验证器 App 当前显示的 6 位动态验证码。' }}
          </p>

          <div v-if="totpState.stage === 'setup'" class="totp-setup-card">
            <div class="totp-qr-section">
              <div v-if="totpQrCodeUrl" class="totp-qr-box">
                <img :src="totpQrCodeUrl" alt="TOTP 绑定二维码" class="totp-qr-image" />
              </div>
              <div class="totp-qr-copy">
                <span class="totp-qr-title">扫码绑定</span>
                <span class="totp-qr-hint">支持 Google Authenticator、Microsoft Authenticator、2FAS、腾讯身份验证器等兼容 TOTP 的 App。</span>
                <span class="totp-qr-hint">若当前 App 支持令牌激活，也可直接复制下方绑定链接导入。</span>
              </div>
            </div>
            <div class="totp-meta">
              <span>发行方</span>
              <strong>{{ totpState.issuer }}</strong>
            </div>
            <div class="totp-meta">
              <span>登录账号</span>
              <strong>{{ loginForm.username }}</strong>
            </div>
            <div class="totp-secret-box">{{ totpState.secret }}</div>
            <div class="totp-actions">
              <n-button secondary type="primary" @click="copyText(totpState.secret, '密钥已复制')">复制密钥</n-button>
              <n-button secondary @click="copyText(totpState.otpauthUrl, '绑定链接已复制')">复制绑定链接</n-button>
            </div>
          </div>

          <n-form-item label="动态验证码">
            <n-input
              v-model:value="totpState.code"
              placeholder="请输入 6 位数字验证码"
              maxlength="6"
              size="large"
              @keyup.enter="handleTotpSubmit"
            >
              <template #prefix>
                <ShieldCheck class="input-icon" />
              </template>
            </n-input>
          </n-form-item>

          <n-button
            type="primary"
            block
            size="large"
            :loading="loading"
            class="submit-btn"
            @click="handleTotpSubmit"
          >
            {{ totpState.stage === 'setup' ? '完成绑定并登录' : '验证并登录' }}
          </n-button>

          <div class="switch-mode-row">
            <span>需要重新输入账号密码？</span>
            <n-button text type="primary" class="switch-link" @click="handleTotpBack">返回登录</n-button>
          </div>
        </div>
      </n-form>

      <!-- 2. 登录表单 -->
      <n-form
        v-else-if="isLoginMode"
        :model="loginForm"
        ref="loginFormRef"
        :rules="loginRules"
        class="login-form"
      >
        <n-form-item label="登录账号" path="username">
          <n-input
            v-model:value="loginForm.username"
            placeholder="请输入登录账号"
            size="large"
            clearable
          >
            <template #prefix>
              <User class="input-icon" />
            </template>
          </n-input>
        </n-form-item>

        <n-form-item label="安全密码" path="password">
          <n-input
            v-model:value="loginForm.password"
            type="password"
            show-password-on="mousedown"
            placeholder="请输入您的安全密码"
            size="large"
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <Lock class="input-icon" />
            </template>
          </n-input>
        </n-form-item>

        <n-button
          type="primary"
          block
          size="large"
          :loading="loading"
          class="submit-btn"
          @click="handleLogin"
        >
          安全登入空间
        </n-button>

        <div class="switch-mode-row">
          <span>还没有账号？</span>
          <n-button text type="primary" class="switch-link" @click="toggleMode(false)">立即创建账户</n-button>
        </div>
      </n-form>

      <!-- 3. 注册表单 -->
      <n-form
        v-else
        :model="registerForm"
        ref="registerFormRef"
        :rules="registerRules"
        class="login-form"
      >
        <n-form-item label="个性账号" path="username">
          <n-input
            v-model:value="registerForm.username"
            placeholder="设置 3 位及以上字母/数字的账号"
            size="large"
            clearable
          >
            <template #prefix>
              <User class="input-icon" />
            </template>
          </n-input>
        </n-form-item>

        <n-form-item label="昵称 (显示名称)" path="nickname">
          <n-input
            v-model:value="registerForm.nickname"
            placeholder="设置您在空间内展现的昵称"
            size="large"
            clearable
          >
            <template #prefix>
              <Smile class="input-icon" />
            </template>
          </n-input>
        </n-form-item>

        <n-form-item label="安全密码" path="password">
          <n-input
            v-model:value="registerForm.password"
            type="password"
            show-password-on="mousedown"
            :placeholder="PASSWORD_REQUIREMENT_TEXT"
            size="large"
          >
            <template #prefix>
              <Lock class="input-icon" />
            </template>
          </n-input>
        </n-form-item>

        <n-form-item label="再次确认密码" path="confirmPassword">
          <n-input
            v-model:value="registerForm.confirmPassword"
            type="password"
            show-password-on="mousedown"
            placeholder="请重复输入刚才的密码"
            size="large"
            @keyup.enter="handleRegister"
          >
            <template #prefix>
              <ShieldCheck class="input-icon" />
            </template>
          </n-input>
        </n-form-item>

        <n-button
          type="warning"
          block
          size="large"
          :loading="loading"
          class="submit-btn register"
          @click="handleRegister"
        >
          立即注册云端账户
        </n-button>

        <div class="switch-mode-row">
          <span>已经有账号了？</span>
          <n-button text type="warning" class="switch-link" @click="toggleMode(true)">返回登录</n-button>
        </div>
      </n-form>
    </n-card>

    <SliderCaptcha
      :visible="captchaVisible"
      @success="onCaptchaSuccess"
      @close="onCaptchaClose"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { FormInst } from 'naive-ui'
import { gsap } from 'gsap'
import QRCode from 'qrcode'
import { authApi } from '@/api/auth'
import { usePlatformStore } from '@/store/platform'
import { User, Lock, Zap, Smile, ShieldCheck } from 'lucide-vue-next'
import { PASSWORD_REQUIREMENT_TEXT, validatePasswordStrength } from '@/utils/password'
import SliderCaptcha from '@/components/SliderCaptcha.vue'

const router = useRouter()
const message = useMessage()
const platformStore = usePlatformStore()
const platformName = computed(() => platformStore.platformName || 'OmniSpark AI')
const loginRoot = ref<HTMLElement | null>(null)

const isLoginMode = ref(true)
const loading = ref(false)
let loginContext: gsap.Context | null = null
let loginMatchMedia: gsap.MatchMedia | null = null

const loginFormRef = ref<FormInst | null>(null)
const registerFormRef = ref<FormInst | null>(null)

// 登录表单
const loginForm = reactive({
  username: '',
  password: ''
})

// 注册表单
const registerForm = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

type TotpStage = 'none' | 'verify' | 'setup'

const totpState = reactive({
  stage: 'none' as TotpStage,
  loginTicket: '',
  setupTicket: '',
  secret: '',
  otpauthUrl: '',
  issuer: '',
  code: ''
})
const totpQrCodeUrl = ref('')

const shouldReduceMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

const animateFormMode = async () => {
  await nextTick()
  if (!loginRoot.value || shouldReduceMotion()) return

  const targets = Array.from(
    loginRoot.value.querySelectorAll<HTMLElement>('.login-form .n-form-item, .login-form .submit-btn, .switch-mode-row')
  )
  if (targets.length === 0) return

  gsap.killTweensOf(targets)
  gsap.fromTo(
    targets,
    { autoAlpha: 0, y: 18 },
    {
      autoAlpha: 1,
      y: 0,
      duration: 0.38,
      stagger: 0.06,
      ease: 'power2.out',
      clearProps: 'transform,opacity,visibility'
    }
  )
}

const setupLoginMotion = async () => {
  await nextTick()
  const rootEl = loginRoot.value
  if (!rootEl) return

  loginContext?.revert()
  loginMatchMedia?.revert()

  loginContext = gsap.context(() => {
    loginMatchMedia = gsap.matchMedia()
    loginMatchMedia.add(
      {
        isReduced: '(prefers-reduced-motion: reduce)',
        isMobile: '(max-width: 768px)'
      },
      (context) => {
        const { isReduced } = context.conditions as { isReduced: boolean; isMobile: boolean }
        const root = loginRoot.value
        if (!root) return

        const card = root.querySelector('.login-card')
        const logo = root.querySelector('.logo-box')
        const title = root.querySelector('.login-header h2')
        const subtitle = root.querySelector('.login-header .subtitle')
        const bubbles = Array.from(root.querySelectorAll<HTMLElement>('.bubble'))
        const formTargets = Array.from(
          root.querySelectorAll<HTMLElement>('.login-form .n-form-item, .login-form .submit-btn, .switch-mode-row')
        )

        const allTargets = [card, logo, title, subtitle, ...bubbles, ...formTargets].filter(Boolean) as HTMLElement[]
        if (isReduced) {
          gsap.set(allTargets, { clearProps: 'transform,opacity,visibility,filter' })
          return
        }

        const timeline = gsap.timeline({ defaults: { ease: 'power3.out' } })
        timeline
          .fromTo(
            card,
            { autoAlpha: 0, y: 32, scale: 0.96, filter: 'blur(10px)' },
            { autoAlpha: 1, y: 0, scale: 1, filter: 'blur(0px)', duration: 0.7, clearProps: 'transform,opacity,visibility,filter' }
          )
          .fromTo(
            logo,
            { autoAlpha: 0, scale: 0.7, rotation: -18 },
            { autoAlpha: 1, scale: 1, rotation: 0, duration: 0.46, ease: 'back.out(1.6)', clearProps: 'transform,opacity,visibility' },
            '-=0.44'
          )
          .fromTo(
            title,
            { autoAlpha: 0, y: 14 },
            { autoAlpha: 1, y: 0, duration: 0.34, clearProps: 'transform,opacity,visibility' },
            '-=0.28'
          )
          .fromTo(
            subtitle,
            { autoAlpha: 0, y: 10 },
            { autoAlpha: 1, y: 0, duration: 0.28, clearProps: 'transform,opacity,visibility' },
            '-=0.24'
          )
          .fromTo(
            formTargets,
            { autoAlpha: 0, y: 18 },
            {
              autoAlpha: 1,
              y: 0,
              duration: 0.34,
              stagger: 0.06,
              clearProps: 'transform,opacity,visibility'
            },
            '-=0.12'
          )

        const loopTweens: gsap.core.Tween[] = []
        if (bubbles[0]) {
          loopTweens.push(
            gsap.to(bubbles[0], {
              x: 34,
              y: 18,
              scale: 1.08,
              duration: 9,
              repeat: -1,
              yoyo: true,
              ease: 'sine.inOut'
            })
          )
        }
        if (bubbles[1]) {
          loopTweens.push(
            gsap.to(bubbles[1], {
              x: -38,
              y: -20,
              scale: 1.06,
              duration: 11,
              repeat: -1,
              yoyo: true,
              ease: 'sine.inOut'
            })
          )
        }
        if (logo) {
          loopTweens.push(
            gsap.to(logo, {
              y: -4,
              duration: 2.4,
              repeat: -1,
              yoyo: true,
              ease: 'sine.inOut'
            })
          )
        }

        return () => {
          loopTweens.forEach((tween) => tween.kill())
        }
      },
      rootEl
    )
  }, rootEl)
}

// 登录验证规则
const loginRules = {
  username: [
    { required: true, message: '请输入您的用户名/账号', trigger: ['blur', 'input'] }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: ['blur', 'input'] }
  ]
}

// 注册验证规则
const registerRules = {
  username: [
    { required: true, message: '设置账户名称', trigger: 'blur' },
    { min: 3, message: '账号长度不能少于 3 个字符', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '设置您的个性显示昵称', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '设置您的安全密码', trigger: ['blur', 'input'] },
    {
      validator: (_rule: unknown, value: string) => {
        const error = validatePasswordStrength(value, registerForm.username)
        return error ? new Error(error) : true
      },
      trigger: ['blur', 'input']
    }
  ],
  confirmPassword: [
    { required: true, message: '请重复输入密码以确认', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string) => {
        return value === registerForm.password
      },
      message: '两次输入的密码不一致，请核对',
      trigger: ['blur', 'input']
    }
  ]
}

// 切换模式
const toggleMode = (mode: boolean) => {
  isLoginMode.value = mode
  resetTotpState()
  // 清空表单
  registerForm.username = ''
  registerForm.nickname = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
}

// ===== 滑块验证码 =====
const captchaVisible = ref(false)
const pendingAction = ref<'login' | 'register' | null>(null)

// 登录触发：先校验表单，再弹验证码
const handleLogin = () => {
  loginFormRef.value?.validate((errors) => {
    if (errors) return
    pendingAction.value = 'login'
    captchaVisible.value = true
  })
}

// 注册触发：先校验表单，再弹验证码
const handleRegister = () => {
  registerFormRef.value?.validate((errors) => {
    if (errors) return
    pendingAction.value = 'register'
    captchaVisible.value = true
  })
}

// 验证码通过：拿到票据后执行真正的登录/注册
const onCaptchaSuccess = async (ticket: string) => {
  captchaVisible.value = false
  const action = pendingAction.value
  pendingAction.value = null
  if (action === 'login') {
    await doLogin(ticket)
  } else if (action === 'register') {
    await doRegister(ticket)
  }
}

const onCaptchaClose = () => {
  captchaVisible.value = false
  pendingAction.value = null
}

const getErrorMessage = (error: unknown, fallback: string) => {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return fallback
}

const isRetryableTotpError = (messageText: string) =>
  messageText.includes('动态验证码无效')

const resetTotpState = () => {
  totpState.stage = 'none'
  totpState.loginTicket = ''
  totpState.setupTicket = ''
  totpState.secret = ''
  totpState.otpauthUrl = ''
  totpState.issuer = ''
  totpState.code = ''
  totpQrCodeUrl.value = ''
}

const buildTotpQrCode = async (value: string) => {
  const normalized = value.trim()
  if (!normalized) {
    totpQrCodeUrl.value = ''
    return
  }
  try {
    totpQrCodeUrl.value = await QRCode.toDataURL(normalized, {
      errorCorrectionLevel: 'M',
      margin: 1,
      width: 220,
      color: {
        dark: '#0f172a',
        light: '#ffffff'
      }
    })
  } catch {
    totpQrCodeUrl.value = ''
    message.error('绑定二维码生成失败，请先使用密钥或绑定链接完成令牌激活')
  }
}

const activateTotpFlow = (result: Awaited<ReturnType<typeof authApi.login>>) => {
  if (result.type === 'totp') {
    totpState.stage = 'verify'
    totpState.loginTicket = result.loginTicket
    totpState.setupTicket = ''
    totpState.secret = ''
    totpState.otpauthUrl = ''
    totpState.issuer = ''
    totpState.code = ''
    message.warning('检测到管理员账号，请输入动态验证码继续登录')
    return
  }
  if (result.type === 'totp-setup') {
    totpState.stage = 'setup'
    totpState.loginTicket = ''
    totpState.setupTicket = result.setupTicket
    totpState.secret = result.totpSecret
    totpState.otpauthUrl = result.totpOtpauthUrl
    totpState.issuer = result.totpIssuer
    totpState.code = ''
    message.warning('管理员账号首次登录，请先完成验证器绑定')
  }
}

const copyText = async (value: string, successText: string) => {
  if (!value.trim()) {
    message.error('没有可复制的内容')
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    message.success(successText)
  } catch {
    message.error('复制失败，请手动复制')
  }
}

const handleTotpBack = () => {
  resetTotpState()
  loginForm.password = ''
}

const isValidTotpCode = (value: string) => /^\d{6}$/.test(value.trim())

const handleTotpSubmit = async () => {
  if (!isValidTotpCode(totpState.code)) {
    message.error('请输入 6 位数字动态验证码')
    return
  }
  loading.value = true
  try {
    const result = totpState.stage === 'setup'
      ? await authApi.completeTotpSetup({
          setupTicket: totpState.setupTicket,
          totpCode: totpState.code.trim()
        })
      : await authApi.completeTotpLogin({
          loginTicket: totpState.loginTicket,
          totpCode: totpState.code.trim()
        })
    resetTotpState()
    message.success(`登录成功，欢迎回来，${result.userInfo.nickname}👋`)
    router.push('/dashboard')
  } catch (err: unknown) {
    const errorMessage = getErrorMessage(err, '动态验证码校验失败，请重新登录')
    message.error(errorMessage)
    totpState.code = ''
    if (isRetryableTotpError(errorMessage)) {
      return
    }
    handleTotpBack()
  } finally {
    loading.value = false
  }
}

const doLogin = async (captchaTicket: string) => {
  loading.value = true
  try {
    const result = await authApi.login({
      username: loginForm.username,
      password: loginForm.password,
      captchaTicket
    })
    if (result.type === 'success') {
      resetTotpState()
      message.success(`登录成功，欢迎回来，${result.userInfo.nickname}👋`)
      router.push('/dashboard')
      return
    }
    activateTotpFlow(result)
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '账户或安全密码校验失败，请重试！'))
  } finally {
    loading.value = false
  }
}

const doRegister = async (captchaTicket: string) => {
  loading.value = true
  try {
    const createdUser = await authApi.register({
      username: registerForm.username,
      password: registerForm.password,
      nickname: registerForm.nickname,
      captchaTicket
    })
    if (createdUser.username !== registerForm.username) {
      throw new Error('注册结果待确认')
    }
    message.success(`新账号 ${createdUser.username} 注册成功！已为您自动切回登录`)
    loginForm.username = createdUser.username
    loginForm.password = ''
    isLoginMode.value = true
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '账号注册冲突，请稍后重试！'))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void setupLoginMotion()
})

watch(isLoginMode, () => {
  void animateFormMode()
})

watch(() => totpState.otpauthUrl, (value) => {
  void buildTotpQrCode(value)
})

onBeforeUnmount(() => {
  loginMatchMedia?.revert()
  loginContext?.revert()
})
</script>

<style scoped>
.login-container {
  --login-page-bg: var(--bg-color);
  --login-page-spot-left: radial-gradient(circle, rgba(16, 185, 129, 0.14), transparent 72%);
  --login-page-spot-right: radial-gradient(circle, rgba(59, 130, 246, 0.16), transparent 72%);
  --login-bubble-opacity: 0.15;
  --login-card-bg:
    linear-gradient(180deg, rgba(15, 23, 42, 0.72) 0%, rgba(8, 12, 24, 0.88) 100%),
    rgba(15, 23, 42, 0.45);
  --login-card-border: rgba(255, 255, 255, 0.08);
  --login-card-shadow: 0 24px 60px rgba(0, 0, 0, 0.54);
  --login-card-overlay: linear-gradient(135deg, rgba(16, 185, 129, 0.06), transparent 36%, rgba(59, 130, 246, 0.08) 100%);
  --login-subtitle-color: var(--text-muted);
  --login-icon-color: var(--text-muted);
  --login-switch-color: var(--text-muted);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 100vh;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.08), transparent 28%),
    radial-gradient(circle at bottom right, rgba(255, 255, 255, 0.04), transparent 24%),
    var(--login-page-bg);
  overflow: hidden;
  isolation: isolate;
}

:global(body.light) .login-container {
  --login-page-bg: linear-gradient(180deg, #f8fafc 0%, #eef4ff 46%, #f7fbff 100%);
  --login-page-spot-left: radial-gradient(circle, rgba(16, 185, 129, 0.12), transparent 72%);
  --login-page-spot-right: radial-gradient(circle, rgba(59, 130, 246, 0.14), transparent 72%);
  --login-bubble-opacity: 0.22;
  --login-card-bg:
    linear-gradient(180deg, rgba(255, 255, 255, 0.92) 0%, rgba(244, 248, 255, 0.98) 100%),
    rgba(255, 255, 255, 0.92);
  --login-card-border: rgba(148, 163, 184, 0.22);
  --login-card-shadow: 0 28px 72px rgba(148, 163, 184, 0.22);
  --login-card-overlay: linear-gradient(135deg, rgba(16, 185, 129, 0.08), rgba(255, 255, 255, 0) 38%, rgba(59, 130, 246, 0.1) 100%);
}

.login-container::before,
.login-container::after {
  content: '';
  position: absolute;
  border-radius: 999px;
  pointer-events: none;
  filter: blur(36px);
}

.login-container::before {
  width: 300px;
  height: 300px;
  top: -120px;
  left: -90px;
  background: var(--login-page-spot-left);
}

.login-container::after {
  width: 320px;
  height: 320px;
  right: -100px;
  bottom: -120px;
  background: var(--login-page-spot-right);
}

/* 霓虹渐变发光气泡背景 */
.gradient-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
}

.bubble {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: var(--login-bubble-opacity);
}

.bubble-1 {
  top: 20%;
  left: 25%;
  width: 350px;
  height: 350px;
  background: radial-gradient(circle, #10b981 0%, transparent 70%);
  animation: moveBubble1 12s infinite alternate;
}

.bubble-2 {
  bottom: 20%;
  right: 25%;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, #3b82f6 0%, transparent 70%);
  animation: moveBubble2 16s infinite alternate;
}

@keyframes moveBubble1 {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(50px, 30px) scale(1.15); }
}

@keyframes moveBubble2 {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(-60px, -40px) scale(1.1); }
}

/* 磨砂玻璃卡片 */
.glass-card {
  background: var(--login-card-bg) !important;
  backdrop-filter: blur(20px);
  border: 1px solid var(--login-card-border) !important;
  box-shadow: var(--login-card-shadow);
  border-radius: 20px !important;
}

.login-card {
  position: relative;
  overflow: hidden;
  width: 420px;
  padding: 10px;
  z-index: 2;
  transition: all 0.4s ease;
}

.login-card::before {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 19px;
  background: var(--login-card-overlay);
  pointer-events: none;
}

.login-header {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
}

.logo-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #10b981, #3b82f6);
  border-radius: 16px;
  box-shadow: 0 0 24px rgba(16, 185, 129, 0.36);
  margin-bottom: 16px;
}

.logo-icon {
  width: 26px;
  height: 26px;
  color: #fff;
}

.login-header h2 {
  font-size: 28px;
  font-weight: 800;
  margin: 0;
  letter-spacing: 0.5px;
  background: linear-gradient(to right, #10b981, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.subtitle {
  font-size: 13px;
  color: var(--login-subtitle-color);
  margin-top: 8px;
  line-height: 1.7;
  text-align: center;
}

.login-form {
  display: flex;
  flex-direction: column;
}

.input-icon {
  width: 18px;
  height: 18px;
  color: var(--login-icon-color);
}

.submit-btn {
  background: linear-gradient(135deg, #10b981, #3b82f6) !important;
  border: none !important;
  box-shadow: 0 4px 15px rgba(59, 130, 246, 0.3) !important;
}

.submit-btn.register {
  background: linear-gradient(135deg, #f59e0b, #ec4899) !important;
  box-shadow: 0 4px 15px rgba(245, 158, 11, 0.3) !important;
  color: #fff !important;
}

.submit-btn:hover {
  opacity: 0.95;
}

.switch-mode-row {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  margin-top: 18px;
  font-size: 13px;
  color: var(--login-switch-color);
}

.switch-link {
  font-weight: 600;
}

.totp-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.totp-panel h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.totp-setup-card {
  position: relative;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(59, 130, 246, 0.18);
  background: rgba(15, 23, 42, 0.16);
}

:global(body.light) .totp-setup-card {
  background: rgba(255, 255, 255, 0.82);
  border-color: rgba(59, 130, 246, 0.16);
}

.totp-qr-section {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.totp-qr-box {
  flex: 0 0 auto;
  padding: 10px;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12);
}

.totp-qr-image {
  display: block;
  width: 132px;
  height: 132px;
  object-fit: contain;
}

.totp-qr-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.totp-qr-title {
  font-size: 15px;
  font-weight: 700;
}

.totp-qr-hint {
  font-size: 12px;
  line-height: 1.55;
  color: var(--login-switch-color);
}

.totp-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 13px;
}

.totp-secret-box {
  margin-top: 12px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.68);
  color: #e2e8f0;
  font-family: "Consolas", "Courier New", monospace;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

:global(body.light) .totp-secret-box {
  background: rgba(226, 232, 240, 0.9);
  color: #0f172a;
}

.totp-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

@media (max-width: 640px) {
  .login-card {
    width: min(420px, calc(100vw - 28px));
  }

  .totp-actions {
    flex-direction: column;
  }

  .totp-qr-section {
    flex-direction: column;
    align-items: stretch;
  }

  .totp-qr-box {
    align-self: center;
  }
}
</style>
