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
        <h2>OmniSpark AI</h2>
        <p class="subtitle">{{ isLoginMode ? '一体化高保真生图与视频创作平台' : '加入多维 AI 创意空间' }}</p>
      </div>

      <!-- 1. 登录表单 -->
      <n-form
        v-if="isLoginMode"
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

      <!-- 2. 注册表单 -->
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
import { ref, reactive, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { FormInst } from 'naive-ui'
import { gsap } from 'gsap'
import { authApi } from '@/api/auth'
import { User, Lock, Zap, Smile, ShieldCheck } from 'lucide-vue-next'
import { PASSWORD_REQUIREMENT_TEXT, validatePasswordStrength } from '@/utils/password'
import SliderCaptcha from '@/components/SliderCaptcha.vue'

const router = useRouter()
const message = useMessage()
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
      validator: (_rule: any, value: string) => {
        const error = validatePasswordStrength(value, registerForm.username)
        return error ? new Error(error) : true
      },
      trigger: ['blur', 'input']
    }
  ],
  confirmPassword: [
    { required: true, message: '请重复输入密码以确认', trigger: 'blur' },
    {
      validator: (_rule: any, value: string) => {
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

const doLogin = async (captchaTicket: string) => {
  loading.value = true
  try {
    const result = await authApi.login({
      username: loginForm.username,
      password: loginForm.password,
      captchaTicket
    })
    message.success(`登录成功，欢迎回来，${result.userInfo.nickname}👋`)
    router.push('/dashboard')
  } catch (err: any) {
    message.error(err.message || '账户或安全密码校验失败，请重试！')
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
  } catch (err: any) {
    message.error(err.message || '账号注册冲突，请稍后重试！')
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

@media (max-width: 640px) {
  .login-card {
    width: min(420px, calc(100vw - 28px));
  }
}
</style>
