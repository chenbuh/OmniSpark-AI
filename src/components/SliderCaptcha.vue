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
import { computed, ref, watch } from 'vue'
import { captchaApi, type CaptchaData } from '@/api/captcha'

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

const stageRef = ref<HTMLElement | null>(null)

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

async function reload() {
  loading.value = true
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
    data.value = await captchaApi.generate()
    if (data.value.type === 'track' && data.value.trackPath?.length) {
      trackX.value = data.value.trackPath[0][0]
    }
    startTime = Date.now()
  } catch (e: any) {
    hint.value = e?.message || '加载失败，请重试'
    hintError.value = true
  } finally {
    loading.value = false
  }
}

function close() {
  emit('close')
}

function pushTrail(x: number, y: number) {
  trail.value.push([Math.round(x), Math.round(y), Date.now() - startTime])
}

function showError(msg: string) {
  hint.value = msg
  hintError.value = true
  setTimeout(reload, 700)
}

async function doVerify(payload: any) {
  if (verifying.value) return
  verifying.value = true
  const normalizedPayload = {
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
  } catch (e: any) {
    showError(e?.message || '验证未通过，请重试')
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
    reload()
  } else {
    tracking = false
    removeTrackListeners()
  }
})
</script>

<style scoped>
.captcha-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(2px);
}
.captcha-panel {
  background: #11151c;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  padding: 16px;
  width: 360px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
}
.captcha-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.captcha-prompt { color: #e8eaed; font-size: 14px; font-weight: 500; }
.captcha-actions { display: flex; gap: 8px; }
.captcha-icon-btn {
  background: transparent;
  border: none;
  color: #9aa0a6;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}
.captcha-icon-btn:hover { color: #fff; }
.captcha-loading { color: #9aa0a6; text-align: center; padding: 40px 0; }
.captcha-body { display: flex; flex-direction: column; align-items: center; }
.rotate-stage {
  width: 160px;
  height: 160px;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0a0d12;
}
.rotate-img { width: 150px; height: 150px; transition: transform 0.05s linear; }
.rotate-bar { display: flex; align-items: center; gap: 10px; width: 100%; margin-top: 14px; }
.rotate-bar input[type="range"] { flex: 1; accent-color: #5b8cff; }
.captcha-confirm {
  background: #5b8cff;
  border: none;
  color: #fff;
  border-radius: 6px;
  padding: 6px 14px;
  cursor: pointer;
  font-size: 13px;
}
.captcha-confirm:hover { background: #4a7bf0; }
.canvas-stage {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  user-select: none;
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
  background: #fff;
  border: 3px solid #5b8cff;
  cursor: grab;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
}
.track-knob:active { cursor: grabbing; }
.captcha-hint { margin-top: 10px; font-size: 13px; color: #6ad08a; }
.captcha-hint.error { color: #ff6b6b; }
</style>
