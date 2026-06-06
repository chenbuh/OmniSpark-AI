<template>
  <div v-if="visible" class="captcha-mask" @click.self="close">
    <div class="captcha-panel">
      <div class="captcha-head">
        <span class="captcha-prompt">{{ data?.prompt || '安全验证' }}</span>
        <div class="captcha-actions">
          <button class="captcha-icon-btn" title="刷新" @click="reload">↻</button>
          <button class="captcha-icon-btn" title="关闭" @click="close">✕</button>
        </div>
      </div>

      <div v-if="loading" class="captcha-loading">加载中…</div>

      <div v-else-if="data" class="captcha-body">
        <!-- 形态一：旋转校正 -->
        <div v-if="data.type === 'rotate'" class="rotate-stage">
          <img
            :src="data.image"
            class="rotate-img"
            :style="{ transform: `rotate(${rotateAngle}deg)` }"
            draggable="false"
          />
        </div>

        <!-- 形态二：多点顺序点击 / 形态三：轨迹拖动 共用画布 -->
        <div
          v-else
          ref="stageRef"
          class="canvas-stage"
          :style="{ width: data.width + 'px', height: data.height + 'px' }"
          @click="onStageClick"
        >
          <img :src="data.image" class="stage-img" draggable="false" />
          <!-- sequence 已点标记 -->
          <template v-if="data.type === 'sequence'">
            <span
              v-for="(p, i) in clickPoints"
              :key="i"
              class="click-dot"
              :style="{ left: p[0] + 'px', top: p[1] + 'px' }"
            >{{ i + 1 }}</span>
          </template>
          <!-- track 滑块 -->
          <span
            v-if="data.type === 'track'"
            class="track-knob"
            :style="{ left: trackX + 'px', top: trackY + 'px' }"
            @mousedown="startTrack"
            @touchstart.prevent="startTrack"
          ></span>
        </div>

        <!-- rotate 的转动滑条 -->
        <div v-if="data.type === 'rotate'" class="rotate-bar">
          <input
            type="range"
            min="0"
            max="359"
            v-model.number="rotateAngle"
            @input="recordRotateTrail"
            @change="submitRotate"
          />
          <button class="captcha-confirm" @click="submitRotate">确认</button>
        </div>

        <p v-if="hint" class="captcha-hint" :class="{ error: hintError }">{{ hint }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { captchaApi, type CaptchaData, type CaptchaVerifyPayload } from '@/api/captcha'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  (e: 'success', ticket: string): void
  (e: 'close'): void
}>()

const loading = ref(false)
const data = ref<CaptchaData | null>(null)
const hint = ref('')
const hintError = ref(false)
const verifying = ref(false)

// rotate
const rotateAngle = ref(0)
// sequence
const clickPoints = ref<number[][]>([])
// track
const trackX = ref(0)
let tracking = false
let trackOffsetX = 0

// 行为轨迹采集（三形态通用）：[x, y, t]
const trail = ref<number[][]>([])
let startTime = 0
let reloadTimer: ReturnType<typeof setTimeout> | null = null
let latestReloadToken = 0

type CaptchaVerifyDraft = Omit<CaptchaVerifyPayload, 'trail'> & {
  trail?: number[][]
}

const stageRef = ref<HTMLElement | null>(null)

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return fallback
}

const trackY = computed(() => {
  const captcha = data.value
  if (!captcha || captcha.type !== 'track') return 0

  const path = captcha.trackPath
  if (!path?.length) return captcha.trackY || 0

  if (trackX.value <= path[0][0]) return path[0][1]
  for (let i = 1; i < path.length; i++) {
    const prev = path[i - 1]
    const curr = path[i]
    if (trackX.value <= curr[0]) {
      const span = curr[0] - prev[0]
      if (span <= 0) return curr[1]
      const ratio = (trackX.value - prev[0]) / span
      return Math.round(prev[1] + (curr[1] - prev[1]) * ratio)
    }
  }

  return path[path.length - 1][1]
})

function clearReloadTimer() {
  if (reloadTimer !== null) {
    clearTimeout(reloadTimer)
    reloadTimer = null
  }
}

async function reload() {
  const reloadToken = ++latestReloadToken
  loading.value = true
  clearReloadTimer()
  hint.value = ''
  hintError.value = false
  verifying.value = false
  clickPoints.value = []
  rotateAngle.value = 0
  trail.value = []
  tracking = false
  trackOffsetX = 0
  removeTrackListeners()
  try {
    const nextData = await captchaApi.generate()
    if (!props.visible || reloadToken !== latestReloadToken) {
      return
    }
    data.value = nextData
    if (data.value.type === 'track' && data.value.trackPath?.length) {
      trackX.value = data.value.trackPath[0][0]
    }
    startTime = Date.now()
  } catch (e: unknown) {
    if (!props.visible || reloadToken !== latestReloadToken) {
      return
    }
    hint.value = getErrorMessage(e, '加载失败，请重试')
    hintError.value = true
  } finally {
    if (reloadToken === latestReloadToken) {
      loading.value = false
    }
  }
}

function close() {
  clearReloadTimer()
  latestReloadToken++
  verifying.value = false
  tracking = false
  removeTrackListeners()
  emit('close')
}

function pushTrail(x: number, y: number) {
  trail.value.push([Math.round(x), Math.round(y), Date.now() - startTime])
}

function showError(msg: string) {
  hint.value = msg
  hintError.value = true
  clearReloadTimer()
  if (!props.visible) {
    return
  }
  reloadTimer = setTimeout(() => {
    reloadTimer = null
    if (props.visible) {
      void reload()
    }
  }, 700)
}

async function doVerify(payload: CaptchaVerifyDraft) {
  if (verifying.value) return
  verifying.value = true
  const normalizedPayload: CaptchaVerifyPayload = {
    ...payload,
    trail: trail.value.map(p => [...p])
  }
  if (Array.isArray(payload.points)) {
    normalizedPayload.points = payload.points.map((p: number[]) => [
      Math.round(p[0]),
      Math.round(p[1])
    ])
  }

  try {
    const ticket = await captchaApi.verify(normalizedPayload)
    hint.value = '验证通过'
    hintError.value = false
    emit('success', ticket)
  } catch (e: unknown) {
    showError(getErrorMessage(e, '验证未通过，请重试'))
  } finally {
    verifying.value = false
  }
}

// ===== rotate =====
function recordRotateTrail() {
  pushTrail(rotateAngle.value, 0)
}

function submitRotate() {
  if (verifying.value || !data.value || data.value.type !== 'rotate') return
  pushTrail(rotateAngle.value, 0)
  doVerify({ captchaId: data.value.captchaId, angle: rotateAngle.value })
}

// ===== sequence =====
function onStageClick(e: MouseEvent) {
  if (verifying.value || !data.value || data.value.type !== 'sequence') return
  const need = data.value.sequenceLabels?.length || 0
  if (need <= 0 || clickPoints.value.length >= need) return

  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  pushTrail(x, y)
  clickPoints.value.push([x, y])
  if (clickPoints.value.length === need) {
    doVerify({ captchaId: data.value.captchaId, points: clickPoints.value })
  }
}

// ===== track =====
function startTrack(e: MouseEvent | TouchEvent) {
  if (verifying.value || !data.value || data.value.type !== 'track' || !stageRef.value) return
  if ('button' in e && e.button !== 0) return

  const rect = stageRef.value.getBoundingClientRect()
  trackOffsetX = clientX(e) - rect.left - trackX.value
  tracking = true
  pushTrail(trackX.value, trackY.value)
  window.addEventListener('mousemove', onTrackMove)
  window.addEventListener('mouseup', endTrack)
  window.addEventListener('touchmove', onTrackMove, { passive: false })
  window.addEventListener('touchend', endTrack)
  window.addEventListener('touchcancel', endTrack)
}

function removeTrackListeners() {
  window.removeEventListener('mousemove', onTrackMove)
  window.removeEventListener('mouseup', endTrack)
  window.removeEventListener('touchmove', onTrackMove)
  window.removeEventListener('touchend', endTrack)
  window.removeEventListener('touchcancel', endTrack)
}

function clientX(e: MouseEvent | TouchEvent) {
  return 'touches' in e ? e.touches[0]?.clientX ?? 0 : (e as MouseEvent).clientX
}

function onTrackMove(e: MouseEvent | TouchEvent) {
  if (!tracking || !data.value || !stageRef.value) return
  if ('preventDefault' in e) e.preventDefault()
  const rect = stageRef.value.getBoundingClientRect()
  let x = clientX(e) - rect.left - trackOffsetX
  x = Math.max(0, Math.min(x, data.value.width - 1))
  trackX.value = x
  pushTrail(x, trackY.value)
}

function endTrack() {
  if (!tracking) return
  tracking = false
  removeTrackListeners()
  if (!data.value || data.value.type !== 'track') return
  pushTrail(trackX.value, trackY.value)
  doVerify({ captchaId: data.value.captchaId, x: Math.round(trackX.value) })
}

watch(() => props.visible, (v) => {
  if (v) {
    void reload()
  } else {
    clearReloadTimer()
    latestReloadToken++
    loading.value = false
    verifying.value = false
    tracking = false
    removeTrackListeners()
  }
})

onBeforeUnmount(() => {
  clearReloadTimer()
  removeTrackListeners()
})
</script>

<style scoped>
.captcha-mask {
  --captcha-panel-bg: #11151c;
  --captcha-panel-border: rgba(255, 255, 255, 0.12);
  --captcha-panel-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
  --captcha-title-color: #e8eaed;
  --captcha-action-color: #9aa0a6;
  --captcha-action-hover: #fff;
  --captcha-loading-color: #9aa0a6;
  --captcha-stage-bg: #0a0d12;
  --captcha-stage-border: rgba(255, 255, 255, 0.15);
  --captcha-confirm-bg: #5b8cff;
  --captcha-confirm-hover: #4a7bf0;
  --captcha-confirm-text: #fff;
  --captcha-knob-bg: #fff;
  --captcha-knob-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
  --captcha-hint-success: #6ad08a;
  --captcha-hint-error: #ff6b6b;
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(2px);
}

:global(body.light) .captcha-mask {
  --captcha-panel-bg: rgba(255, 255, 255, 0.96);
  --captcha-panel-border: rgba(148, 163, 184, 0.22);
  --captcha-panel-shadow: 0 20px 48px rgba(148, 163, 184, 0.22);
  --captcha-title-color: #1f2937;
  --captcha-action-color: #64748b;
  --captcha-action-hover: #0f172a;
  --captcha-loading-color: #64748b;
  --captcha-stage-bg: #f8fafc;
  --captcha-stage-border: rgba(148, 163, 184, 0.24);
  --captcha-hint-success: #059669;
  --captcha-hint-error: #dc2626;
}

.captcha-panel {
  background: var(--captcha-panel-bg);
  border: 1px solid var(--captcha-panel-border);
  border-radius: 12px;
  padding: 16px;
  width: min(360px, calc(100vw - 24px));
  box-shadow: var(--captcha-panel-shadow);
}
.captcha-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.captcha-prompt { color: var(--captcha-title-color); font-size: 14px; font-weight: 500; }
.captcha-actions { display: flex; gap: 8px; }
.captcha-icon-btn {
  background: transparent;
  border: none;
  color: var(--captcha-action-color);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  border-radius: 8px;
  padding: 4px;
  transition: background-color 0.2s ease, color 0.2s ease;
}
.captcha-icon-btn:hover {
  color: var(--captcha-action-hover);
  background: rgba(148, 163, 184, 0.14);
}
.captcha-loading { color: var(--captcha-loading-color); text-align: center; padding: 40px 0; }
.captcha-body { display: flex; flex-direction: column; align-items: center; width: 100%; }
.rotate-stage {
  width: 160px;
  height: 160px;
  max-width: 100%;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid var(--captcha-stage-border);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--captcha-stage-bg);
}
.rotate-img { width: 150px; height: 150px; transition: transform 0.05s linear; }
.rotate-bar { display: flex; align-items: center; gap: 10px; width: 100%; margin-top: 14px; flex-wrap: wrap; }
.rotate-bar input[type="range"] { flex: 1; accent-color: #5b8cff; }
.captcha-confirm {
  background: var(--captcha-confirm-bg);
  border: none;
  color: var(--captcha-confirm-text);
  border-radius: 6px;
  padding: 6px 14px;
  cursor: pointer;
  font-size: 13px;
}
.captcha-confirm:hover { background: var(--captcha-confirm-hover); }
.canvas-stage {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  user-select: none;
  max-width: 100%;
  border: 1px solid var(--captcha-stage-border);
  background: var(--captcha-stage-bg);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}
.stage-img { display: block; width: 100%; height: 100%; pointer-events: none; }
.click-dot {
  position: absolute;
  width: 22px;
  height: 22px;
  margin: -11px 0 0 -11px;
  border-radius: 50%;
  background: #5b8cff;
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.4);
}
.track-knob {
  position: absolute;
  width: 28px;
  height: 28px;
  margin: -14px 0 0 -14px;
  border-radius: 50%;
  background: var(--captcha-knob-bg);
  border: 3px solid #5b8cff;
  cursor: grab;
  box-shadow: var(--captcha-knob-shadow);
}
.track-knob:active { cursor: grabbing; }
.captcha-hint { margin-top: 10px; font-size: 13px; color: var(--captcha-hint-success); }
.captcha-hint.error { color: var(--captcha-hint-error); }

@media (max-width: 480px) {
  .captcha-panel {
    padding: 14px;
  }

  .rotate-bar {
    gap: 8px;
  }

  .captcha-confirm {
    width: 100%;
  }
}
</style>
