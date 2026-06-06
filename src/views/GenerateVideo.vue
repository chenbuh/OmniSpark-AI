<template>
  <div class="generate-container">
    <n-row :gutter="24" style="height: 100%;">
      <!-- 左侧控制面板 -->
      <n-col :span="9" style="height: 100%;">
        <n-card class="glass-card control-card" content-style="display: flex; flex-direction: column; height: 100%;">
          <n-tabs v-model:value="videoMode" type="line" justify-content="space-evenly" class="mode-tabs" @update:value="handleModeChange">
            <n-tab-pane name="txt2vid" tab="文生视频" />
            <n-tab-pane name="img2vid" tab="图生视频" />
          </n-tabs>

          <n-scrollbar class="form-scrollbar">
            <n-form label-placement="top" size="medium" style="padding-right: 8px;">
              <!-- 提供商 -->
              <n-form-item label="视频模型提供商">
                <n-select
                  v-model:value="form.providerId"
                  :options="providerOptions"
                  placeholder="选择已配置的提供商..."
                  @update:value="handleProviderChange"
                />
              </n-form-item>

              <!-- 模型 -->
              <n-form-item label="基础模型">
                <n-select
                  v-model:value="form.modelName"
                  filterable
                  tag
                  :options="modelOptions"
                  placeholder="请选择模型..."
                />
              </n-form-item>
              <div v-if="providerLoadState === 'error'" class="form-status form-status--error">
                视频模型提供商待确认，请稍后重试。
              </div>
              <div v-else-if="providerLoadState === 'ready' && providerOptions.length === 0" class="form-status">
                当前项目暂无可用视频提供商，请先前往模型配置。
              </div>
              <div v-if="metaLoadState === 'error'" class="form-status form-status--error">
                视频时长与镜头配置待确认，请稍后重试。
              </div>

              <!-- 图生视频：首帧 + 尾帧 -->
              <div v-if="videoMode === 'img2vid'" class="ref-section">
                <!-- 双帧过渡预览 -->
                <div class="dual-frame-preview" v-if="selectedImageAsset || selectedEndAsset">
                  <div class="frame-box" :class="{ 'frame-active': !!selectedImageAsset }">
                    <span class="frame-label">首帧</span>
                    <img v-if="selectedImageAsset" :src="selectedImageAsset.thumbUrl" alt="视频首帧" class="frame-img" />
                    <div v-else class="frame-placeholder">未设置</div>
                  </div>
                  <div class="frame-arrow">
                    <ArrowRight class="arrow-icon" />
                    <span class="arrow-text">过渡</span>
                  </div>
                  <div class="frame-box" :class="{ 'frame-active': !!selectedEndAsset }">
                    <span class="frame-label">尾帧</span>
                    <img v-if="selectedEndAsset" :src="selectedEndAsset.thumbUrl" alt="视频尾帧" class="frame-img" />
                    <div v-else class="frame-placeholder">未设置</div>
                  </div>
                </div>

                <!-- 首帧选择 -->
                <div class="ref-picker-row">
                  <div class="ref-picker-block">
                    <span class="ref-label">首帧图像</span>
                    <div class="ref-preview-box mini" @click="openAssetPicker('start')">
                      <div v-if="selectedImageAsset" class="ref-preview-active mini">
                        <img :src="selectedImageAsset.thumbUrl" alt="首帧图像预览" class="ref-preview-img" loading="lazy" />
                        <div class="remove-ref-btn" @click.stop="handleClearRefImage">清除</div>
                      </div>
                      <div v-else class="ref-preview-empty mini">
                        <Image class="empty-ref-icon small" />
                        <span>点击选择</span>
                      </div>
                    </div>
                  </div>
                  <div class="ref-picker-block">
                    <span class="ref-label">尾帧 (结束帧)</span>
                    <div class="ref-preview-box mini" @click="openAssetPicker('end')">
                      <div v-if="selectedEndAsset" class="ref-preview-active mini">
                        <img :src="selectedEndAsset.thumbUrl" alt="尾帧图像预览" class="ref-preview-img" loading="lazy" />
                        <div class="remove-ref-btn" @click.stop="handleClearEndImage">清除</div>
                      </div>
                      <div v-else class="ref-preview-empty mini">
                        <Image class="empty-ref-icon small" />
                        <span>点击选择</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 提示词 Prompt -->
              <n-form-item label="运动提示词指令 (Video Prompt)">
                <n-input
                  v-model:value="form.prompt"
                  type="textarea"
                  :autosize="{ minRows: 3, maxRows: 6 }"
                  placeholder="描述您想让视频呈现的动态镜头、动作或变换 (例如: 太空飞船正在越过恒星，镜头平缓推近，耀眼光斑)"
                />
              </n-form-item>

              <!-- 视频时间 -->
              <n-form-item label="视频生成时间">
                <div class="duration-group">
                  <div
                    v-for="item in durations"
                    :key="item.value"
                    class="duration-card"
                    :class="{ 'active': form.duration === item.value }"
                    @click="form.duration = item.value"
                  >
                    <span class="duration-sec">{{ item.label }}</span>
                    <span class="duration-cost">{{ item.tag }}</span>
                  </div>
                </div>
              </n-form-item>

              <!-- 高级镜头控制 -->
              <n-collapse style="margin-top: 15px;">
                <n-collapse-item title="镜头运动控制与选项" name="1">
                  <n-form-item label="镜头运动方向">
                    <n-select v-model:value="form.cameraMotion" :options="cameraMotionOptions" />
                  </n-form-item>
                  <n-form-item label="运动强度 (Motion Speed)">
                    <n-slider v-model:value="form.motionSpeed" :min="1" :max="10" :step="1" />
                    <div class="slider-val">强度: {{ form.motionSpeed }}</div>
                  </n-form-item>
                </n-collapse-item>
              </n-collapse>
            </n-form>
          </n-scrollbar>

          <div class="btn-area">
            <n-space style="width:100%;">
              <n-button type="warning" block size="large" class="generate-btn" :loading="generating" @click="handleStartGenerate">
                <Zap class="gen-zap" /> 立即构建视频
              </n-button>
            </n-space>
            <div style="text-align:center;margin-top:6px;">
              <n-button size="tiny" quaternary @click="clearForm">重置表单</n-button>
            </div>
          </div>
        </n-card>
      </n-col>

      <!-- 右侧预览区 -->
      <n-col :span="15" style="height: 100%;">
        <n-card class="glass-card preview-card">
          <!-- 1. 生成中 -->
          <div v-if="generating || (activeTask && (activeTask.status === 'pending' || activeTask.status === 'running'))" class="loading-state">
            <div class="pulsing-glow"></div>
            <n-progress
              v-if="activeTaskProgress !== null"
              type="circle"
              :percentage="activeTaskProgress"
              :color="'#f59e0b'"
              :rail-color="'rgba(255, 255, 255, 0.05)'"
              :stroke-width="6"
              :width="160"
            >
              <template #default>
                <div class="progress-inner">
                  <span class="pct" style="color: #f59e0b;">{{ activeTaskProgress }}%</span>
                  <span class="time">任务进行中</span>
                </div>
              </template>
            </n-progress>
            <div v-else class="progress-pending">
              <n-spin size="large" />
              <div class="progress-inner progress-inner--pending">
                <span class="pct" style="color: #f59e0b;">{{ generating ? '提交中' : '待同步' }}</span>
                <span class="time">{{ generating ? '正在提交任务...' : '任务进行中' }}</span>
              </div>
            </div>
            <div class="loading-info">
              <h3>视频多帧合成中...</h3>
              <p class="progress-step-text" style="color: #f59e0b;">{{ generating ? '正在向模型服务提交视频生成请求' : (activeTask?.progressText || '正在同步视频任务进度...') }}</p>
            </div>
          </div>

          <!-- 2. 生成成功播放器 -->
          <div v-else-if="currentAsset" class="result-state">
            <div class="video-wrapper">
              <video :src="currentAsset.fileUrl" :aria-label="currentAsset.prompt || '生成的视频结果'" class="result-video" controls autoplay loop playsinline></video>
              <!-- 悬浮控制条 -->
              <div class="action-float-bar">
                <n-button circle secondary class="float-btn" @click="handleToggleFavorite">
                  <template #icon>
                    <Heart :class="{ 'favorited': currentAsset.favorite }" />
                  </template>
                </n-button>
                <n-button circle secondary class="float-btn" @click="handleDownload">
                  <template #icon>
                    <Download />
                  </template>
                </n-button>
              </div>
            </div>

            <!-- 参数详情 -->
            <div class="params-details-card">
              <div class="params-head">
                <span class="model-badge">视频模型: {{ currentAsset.modelName }}</span>
                <span class="date">{{ currentAsset.createdAt }}</span>
              </div>
              <p class="prompt-display"><strong>视频 Prompt:</strong> {{ currentAsset.prompt }}</p>
            </div>
          </div>

          <!-- 3. 空状态 -->
          <div v-else class="empty-state">
            <div class="empty-glow" style="background: radial-gradient(circle, rgba(245, 158, 11, 0.05) 0%, transparent 70%);"></div>
            <Video class="empty-icon" />
            <h3>{{ emptyStateTitle }}</h3>
            <p>{{ emptyStateDescription }}</p>
          </div>

          <!-- 本空间视频历史 -->
          <div class="history-section" v-if="historyLoadState === 'ready' && taskHistory.length > 0">
            <div class="history-head">
              <span class="history-label">本空间视频历史 ({{ taskHistory.length }})</span>
              <n-button size="tiny" type="error" tertiary @click="handleBatchClear">清空</n-button>
            </div>
            <n-scrollbar x-scrollable>
              <div class="history-row">
                <div
                  v-for="task in taskHistory"
                  :key="task.id"
                  class="history-thumb-box"
                  :class="{ 'active': activeTask?.id === task.id }"
                  @click="handleInspectHistoryTask(task)"
                >
                  <div v-if="task.status === 'running' || task.status === 'pending'" class="thumb-loading-overlay">
                    <n-spin size="small" />
                  </div>
                  <div v-else-if="task.status === 'success'" class="thumb-video-badge">
                    <Tv class="thumb-badge-icon" />
                  </div>
                  <img v-if="task.status === 'success'" :src="getAssetThumbUrl(task.resultAssetId)" :alt="task.prompt || '视频任务缩略图'" class="history-thumb-img" />
                  <div v-else-if="task.status === 'failed'" class="thumb-failed-overlay">
                    <span class="thumb-failed-text">失败</span>
                  </div>
                  <div class="thumb-remove" @click.stop="handleDeleteTask(task.id)">×</div>
                </div>
              </div>
            </n-scrollbar>
          </div>
          <div v-else-if="historyLoadState === 'error'" class="history-status">
            视频历史待确认，请稍后重试。
          </div>
        </n-card>
      </n-col>
    </n-row>

    <!-- 从资产库选择图片资产弹窗 -->
    <n-modal
      v-model:show="showAssetSelectModal"
      preset="card"
      title="从共享资产库选择首帧参考图"
      style="width: 60vw; max-width: 800px;"
    >
      <div class="assets-picker-grid">
        <div
          v-for="asset in imageAssets"
          :key="asset.id"
          class="picker-item"
          @click="handleSelectAssetFromModal(asset)"
        >
          <img :src="asset.thumbUrl" :alt="asset.fileName || '可选资产缩略图'" class="picker-thumb" />
          <div class="picker-info">
            <span class="picker-name">{{ asset.fileName }}</span>
          </div>
        </div>
        <div v-if="assetLibraryLoadState === 'error'" class="picker-empty">
          图片资产待确认，请稍后重试。
        </div>
        <div v-else-if="assetLibraryLoadState === 'loading'" class="picker-empty">
          正在加载图片资产...
        </div>
        <div v-else-if="imageAssets.length === 0" class="picker-empty">
          资产库中尚无图片，请先前往生图页生成一些大作！
        </div>
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useProjectStore } from '@/store/project'
import { useModelProviderStore } from '@/store/provider'
import { useTaskStore } from '@/store/task'
import { useAssetStore, type Asset } from '@/store/asset'
import { assetApi } from '@/api/assets'
import { generationApi, type GenerationMetaOption, type GenerationMetaVO } from '@/api/generation'
import { taskApi } from '@/api/tasks'
import {
  Zap,
  Download,
  Heart,
  Image,
  Video,
  Tv,
  ArrowRight
} from 'lucide-vue-next'

const route = useRoute()
const message = useMessage()

const projectStore = useProjectStore()
const providerStore = useModelProviderStore()
const taskStore = useTaskStore()
const assetStore = useAssetStore()

const videoMode = ref('txt2vid')
const generating = ref(false)
const showAssetSelectModal = ref(false)
const generationMeta = ref<GenerationMetaVO>({})
const metaLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const providerLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const historyLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const assetLibraryLoadState = ref<'loading' | 'ready' | 'error'>('loading')

const selectedImageAsset = ref<Asset | null>(null)
const selectedEndAsset = ref<Asset | null>(null)
const assetPickerMode = ref<'start' | 'end'>('start')
const activeTaskId = ref<number | null>(null)
let taskPollingTimer: ReturnType<typeof setInterval> | null = null
let taskSyncing = false
let pendingFetchAsset: Promise<Asset | null> | null = null

const form = reactive({
  providerId: null as number | null,
  modelName: '',
  prompt: '',
  duration: '',
  cameraMotion: '',
  motionSpeed: 5
})

const durations = computed(() => {
  const options = generationMeta.value.video?.durationOptions || []
  return options.map(item => ({
    label: item.label,
    value: item.value,
    tag: item.value === '10s' ? '深度计算' : '流畅生成'
  }))
})

const cameraMotionOptions = computed(() => generationMeta.value.video?.cameraMotionOptions || [])
const defaultVideoDuration = computed(() => generationMeta.value.video?.defaults?.duration || durations.value[0]?.value || '')
const defaultCameraMotion = computed(() => generationMeta.value.video?.defaults?.cameraMotion || cameraMotionOptions.value[0]?.value || '')

function normalizeTaskField(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function requireStringValue(value: unknown, errorMessage: string) {
  const normalized = typeof value === 'string' ? value.trim() : ''
  if (!normalized) {
    throw new Error(errorMessage)
  }
  return normalized
}

function requireGenerationMetaOptionList(value: unknown, errorMessage: string) {
  if (!Array.isArray(value)) {
    throw new Error(errorMessage)
  }
  const seenValues = new Set<string>()
  return value.map((item: unknown) => {
    if (!isPlainObject(item)) {
      throw new Error(errorMessage)
    }
    const label = requireStringValue(item.label, errorMessage)
    const optionValue = requireStringValue(item.value, errorMessage)
    if (seenValues.has(optionValue)) {
      throw new Error(errorMessage)
    }
    seenValues.add(optionValue)
    return { label, value: optionValue }
  })
}

function requireAllowedProviderTypes(value: unknown, errorMessage: string) {
  if (!Array.isArray(value)) {
    throw new Error(errorMessage)
  }
  const seenValues = new Set<string>()
  return value.map((item: unknown) => {
    const providerType = requireStringValue(item, errorMessage)
    if (seenValues.has(providerType)) {
      throw new Error(errorMessage)
    }
    seenValues.add(providerType)
    return providerType
  })
}

function requireVideoGenerationMeta(value: unknown): GenerationMetaVO {
  const errorMessage = '视频配置待确认'
  if (!isPlainObject(value) || !isPlainObject(value.video)) {
    throw new Error(errorMessage)
  }
  const defaults = value.video.defaults
  if (defaults != null && !isPlainObject(defaults)) {
    throw new Error(errorMessage)
  }
  return {
    video: {
      allowedProviderTypes: requireAllowedProviderTypes(value.video.allowedProviderTypes, errorMessage),
      durationOptions: requireGenerationMetaOptionList(value.video.durationOptions, errorMessage),
      cameraMotionOptions: requireGenerationMetaOptionList(value.video.cameraMotionOptions, errorMessage),
      defaults: defaults
        ? {
            duration: typeof defaults.duration === 'string' ? defaults.duration.trim() : undefined,
            cameraMotion: typeof defaults.cameraMotion === 'string' ? defaults.cameraMotion.trim() : undefined
          }
        : undefined
    }
  }
}

function parseTaskRequestJson(task: { requestJson?: string }, errorMessage: string) {
  if (!task.requestJson) {
    throw new Error(errorMessage)
  }
  try {
    const parsed = JSON.parse(task.requestJson)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error(errorMessage)
    }
    return parsed as Record<string, unknown>
  } catch {
    throw new Error(errorMessage)
  }
}

function assertVideoGenerationTaskMatches(
  task: ReturnType<typeof taskStore.normalizeTask>,
  expected: {
    projectId: number
    providerId: number
    prompt: string
    modelName: string
    sourceAssetId?: number
    endAssetId?: number
    duration: string
    cameraMotion: string
    motionSpeed: number
  }
) {
  const errorMessage = '视频任务提交结果待确认'
  if (task.projectId !== expected.projectId || task.providerId !== expected.providerId || task.taskType !== 'video') {
    throw new Error(errorMessage)
  }
  if (normalizeTaskField(task.prompt) !== expected.prompt) {
    throw new Error(errorMessage)
  }
  if (normalizeTaskField(task.modelName) !== expected.modelName) {
    throw new Error(errorMessage)
  }
  const requestPayload = parseTaskRequestJson(task, errorMessage)
  if (
    Number(requestPayload.projectId) !== expected.projectId
    || Number(requestPayload.providerId) !== expected.providerId
    || normalizeTaskField(requestPayload.prompt) !== expected.prompt
    || normalizeTaskField(requestPayload.modelName) !== expected.modelName
    || normalizeTaskField(requestPayload.size) !== expected.duration
    || Number(requestPayload.sourceAssetId ?? 0) !== Number(expected.sourceAssetId ?? 0)
    || Number(requestPayload.endAssetId ?? 0) !== Number(expected.endAssetId ?? 0)
  ) {
    throw new Error(errorMessage)
  }
  const options = requestPayload.options
  if (!options || typeof options !== 'object' || Array.isArray(options)) {
    throw new Error(errorMessage)
  }
  if (
    normalizeTaskField((options as Record<string, unknown>).modelName) !== expected.modelName
    || normalizeTaskField((options as Record<string, unknown>).cameraMotion) !== expected.cameraMotion
    || Number((options as Record<string, unknown>).motionSpeed) !== expected.motionSpeed
  ) {
    throw new Error(errorMessage)
  }
}

async function confirmSubmittedVideoTask(
  task: ReturnType<typeof taskStore.normalizeTask>,
  expected: {
    projectId: number
    providerId: number
    prompt: string
    modelName: string
    sourceAssetId?: number
    endAssetId?: number
    duration: string
    cameraMotion: string
    motionSpeed: number
  }
) {
  assertVideoGenerationTaskMatches(task, expected)
  await taskStore.refresh({ projectId: expected.projectId })
  const confirmedFromList = taskStore.getTasksByProject(expected.projectId).find(item => item.id === task.id)
  const confirmed = confirmedFromList ?? taskStore.normalizeTask((await taskApi.getTask(task.id)).data)
  assertVideoGenerationTaskMatches(confirmed, expected)
  return confirmed
}

// 空间下的资产库中的所有图片资产（供参考图挑选）
const imageAssets = computed(() => {
  return assetStore
    .getAssetsByProject(projectStore.activeProjectId)
    .filter(a => a.assetType === 'image')
})

const allowedVideoProviderTypes = computed(() => generationMeta.value.video?.allowedProviderTypes || [])

function resolveOptionValue(options: GenerationMetaOption[], preferredValue?: string, fallbackValue = '') {
  if (preferredValue && options.some(option => option.value === preferredValue)) return preferredValue
  return options[0]?.value || fallbackValue
}

async function loadGenerationMeta() {
  metaLoadState.value = 'loading'
  try {
    const res = await generationApi.getMeta()
    generationMeta.value = requireVideoGenerationMeta((res as any).data)
    metaLoadState.value = 'ready'
  } catch {
    generationMeta.value = {}
    metaLoadState.value = 'error'
  }
  form.duration = resolveOptionValue(generationMeta.value.video?.durationOptions || [], form.duration, defaultVideoDuration.value)
  form.cameraMotion = resolveOptionValue(cameraMotionOptions.value, form.cameraMotion, defaultCameraMotion.value)
}

const providerOptions = computed(() => {
  return providerStore
    .getProvidersByProject(projectStore.activeProjectId)
    .filter(p => allowedVideoProviderTypes.value.includes(p.type))
    .map(p => ({
      label: p.name,
      value: p.id
    }))
})

const modelOptions = ref<{ label: string; value: string }[]>([])

// 获取本空间视频生成历史
const taskHistory = computed(() => {
  return taskStore
    .getTasksByProject(projectStore.activeProjectId)
    .filter(t => t.taskType === 'video')
})

const initDefaults = () => {
  const providers = providerStore
    .getProvidersByProject(projectStore.activeProjectId)
    .filter(p => allowedVideoProviderTypes.value.includes(p.type))
  if (providers.length > 0) {
    const defaultProvider = providers.find(p => p.isDefault) || providers[0]
    form.providerId = defaultProvider.id
    handleProviderChange(defaultProvider.id)
  } else {
    form.providerId = null
    modelOptions.value = []
  }
}

const handleProviderChange = (val: number) => {
  const provider = providerStore.providers.find(p => p.id === val)
  if (provider) {
    form.modelName = provider.modelName
    modelOptions.value = provider.modelName ? [{ label: provider.modelName, value: provider.modelName }] : []
  }
}

const generationConfigState = computed<'ready' | 'error' | 'empty'>(() => {
  if (metaLoadState.value === 'error' || providerLoadState.value === 'error') {
    return 'error'
  }
  if (providerLoadState.value === 'ready' && providerOptions.value.length === 0) {
    return 'empty'
  }
  return 'ready'
})

const emptyStateTitle = computed(() => {
  if (generationConfigState.value === 'error') {
    return '视频配置待确认'
  }
  if (generationConfigState.value === 'empty') {
    return '暂无可用视频模型'
  }
  return '开始您的视频艺术渲染'
})

const emptyStateDescription = computed(() => {
  if (generationConfigState.value === 'error') {
    return '当前项目的视频模型或生成参数暂时无法确认，请稍后重试。'
  }
  if (generationConfigState.value === 'empty') {
    return '当前项目还没有配置与视频生成兼容的提供商，先去模型配置页接入后再开始创作。'
  }
  return '在左侧配置模型并键入您的运动创意，高保真动态视频将在右侧为您流畅播放。'
})

async function loadProviders() {
  if (!projectStore.activeProjectId) {
    providerLoadState.value = 'ready'
    return
  }
  providerLoadState.value = 'loading'
  try {
    await providerStore.refresh(projectStore.activeProjectId)
    providerLoadState.value = 'ready'
  } catch {
    providerLoadState.value = 'error'
  }
}

async function loadTaskHistory() {
  if (!projectStore.activeProjectId) {
    historyLoadState.value = 'ready'
    return
  }
  historyLoadState.value = 'loading'
  try {
    await taskStore.refresh({ projectId: projectStore.activeProjectId })
    historyLoadState.value = 'ready'
  } catch {
    historyLoadState.value = 'error'
  }
}

async function loadAssetLibrary() {
  if (!projectStore.activeProjectId) {
    assetLibraryLoadState.value = 'ready'
    return
  }
  assetLibraryLoadState.value = 'loading'
  try {
    await assetStore.refresh({ projectId: projectStore.activeProjectId })
    assetLibraryLoadState.value = 'ready'
  } catch {
    assetLibraryLoadState.value = 'error'
  }
}

async function loadPageContext() {
  await Promise.allSettled([
    loadGenerationMeta(),
    loadProviders(),
    loadTaskHistory(),
    loadAssetLibrary()
  ])
  initDefaults()
}

onMounted(async () => {
  await loadPageContext()
  
  // 处理从生图页“一键转视频”带入的参数
  if (route.query.sourceAssetId) {
    const id = Number(route.query.sourceAssetId)
    const asset = assetStore.assets.find(a => a.id === id)
    if (asset && (asset.assetType === 'image' || asset.assetType === 'reference')) {
      videoMode.value = 'img2vid'
      selectedImageAsset.value = asset
    } else if (asset) {
      message.warning('当前仅支持图片作为首帧参考，已仅带入提示词与模型')
    }
  }
  
  if (route.query.prompt) {
    form.prompt = route.query.prompt as string
  }
})

// 监听项目切换
watch(() => projectStore.activeProjectId, () => {
  selectedImageAsset.value = null
  selectedEndAsset.value = null
  activeTaskId.value = null
  finishGeneratingState()
  void loadPageContext()
})

const activeTask = computed(() => {
  if (activeTaskId.value) {
    return taskStore.tasks.find(t => t.id === activeTaskId.value)
  }
  const hist = taskHistory.value
  if (hist.length > 0) {
    return hist[0]
  }
  return null
})

const activeTaskProgress = computed(() => {
  const progress = activeTask.value?.progress
  return typeof progress === 'number' ? Math.max(0, Math.min(100, progress)) : null
})

const stopTaskPolling = () => {
  if (taskPollingTimer) {
    clearInterval(taskPollingTimer)
    taskPollingTimer = null
  }
}

const finishGeneratingState = () => {
  generating.value = false
  stopTaskPolling()
}

function assertGeneratedVideoAsset(
  asset: Asset,
  task: { id: number; projectId: number; prompt?: string; modelName?: string; resultAssetId?: number }
) {
  if (asset.projectId !== task.projectId) {
    throw new Error('视频结果待确认')
  }
  if (asset.taskId !== task.id) {
    throw new Error('视频结果待确认')
  }
  if (asset.assetType !== 'video') {
    throw new Error('视频结果待确认')
  }
  if (!asset.fileUrl && !asset.thumbUrl) {
    throw new Error('视频结果待确认')
  }
  if (normalizeTaskField(asset.prompt) !== normalizeTaskField(task.prompt)) {
    throw new Error('视频结果待确认')
  }
  if (normalizeTaskField(asset.modelName) !== normalizeTaskField(task.modelName)) {
    throw new Error('视频结果待确认')
  }
}

function getConfirmedVideoTaskAsset(task: { id: number; projectId: number; prompt?: string; modelName?: string; resultAssetId?: number }) {
  const exact = task.resultAssetId
    ? assetStore.assets.find(asset => asset.id === task.resultAssetId)
    : null
  if (exact) {
    assertGeneratedVideoAsset(exact, task)
    return exact
  }
  const fallback = assetStore.assets.find(asset => asset.taskId === task.id) || null
  if (!fallback) {
    throw new Error('视频结果待确认')
  }
  assertGeneratedVideoAsset(fallback, task)
  if (task.resultAssetId && fallback.id !== task.resultAssetId) {
    throw new Error('视频结果待确认')
  }
  return fallback
}

function requireVideoTaskAssetContext(task: { id: number; resultAssetId?: number }) {
  const candidate = (
    (activeTaskId.value === task.id ? activeTask.value : null)
    || taskStore.tasks.find(item => item.id === task.id)
  ) as {
    id: number
    projectId?: number
    prompt?: string
    modelName?: string
    resultAssetId?: number
  } | null
  if (!candidate || !Number.isFinite(candidate.projectId)) {
    throw new Error('视频结果待确认')
  }
  return {
    id: candidate.id,
    projectId: Number(candidate.projectId),
    prompt: candidate.prompt,
    modelName: candidate.modelName,
    resultAssetId: candidate.resultAssetId
  }
}

const upsertAsset = (asset: Asset) => {
  const index = assetStore.assets.findIndex(item => item.id === asset.id)
  if (index === -1) {
    assetStore.assets.unshift(asset)
  } else {
    assetStore.assets[index] = asset
  }
}

const findTaskResultAsset = (task: { id: number; resultAssetId?: number }) => {
  if (task.resultAssetId) {
    const exact = assetStore.assets.find(asset => asset.id === task.resultAssetId && asset.taskId === task.id)
    if (exact) return exact
  }
  return assetStore.assets.find(asset => asset.taskId === task.id) || null
}

const ensureTaskResultAssetLoaded = async (task: { id: number; resultAssetId?: number }) => {
  const taskContext = requireVideoTaskAssetContext(task)
  const existing = findTaskResultAsset(taskContext)
  if (existing) {
    return getConfirmedVideoTaskAsset(taskContext)
  }
  if (!pendingFetchAsset) {
    pendingFetchAsset = (async () => {
      const res = await assetApi.getAssets({ taskId: task.id, projectId: projectStore.activeProjectId })
      const data = (res as any).data
      if (!Array.isArray(data)) {
        throw new Error('视频结果待确认')
      }
      for (const item of data) {
        upsertAsset(assetStore.normalizeAsset(item))
      }
      const confirmedTask = requireVideoTaskAssetContext(task)
      return getConfirmedVideoTaskAsset(confirmedTask)
    })().finally(() => {
      pendingFetchAsset = null
    })
  }
  const asset = await pendingFetchAsset
  if (!asset) {
    throw new Error('视频结果待确认')
  }
  return asset
}

const syncTaskStatus = async (taskId: number) => {
  const detail = await taskApi.getTask(taskId)
  const task = taskStore.upsertTask(detail.data)
  activeTaskId.value = task.id
  if (task.status === 'success') {
    await taskStore.refresh({ projectId: projectStore.activeProjectId })
    await ensureTaskResultAssetLoaded(task)
    finishGeneratingState()
    message.success('视频生成完成')
    assetStore.refresh({ projectId: projectStore.activeProjectId }).catch(() => {})
    return task
  }
  if (task.status === 'failed') {
    finishGeneratingState()
    message.error(task.errorMessage || '视频生成失败')
    return task
  }
  return task
}

const startTaskPolling = (taskId: number) => {
  stopTaskPolling()
  taskPollingTimer = window.setInterval(async () => {
    // 页面隐藏时暂停；上一次同步未完成时跳过，避免请求重叠
    if (document.visibilityState !== 'visible' || taskSyncing) return
    taskSyncing = true
    try {
      await syncTaskStatus(taskId)
    } catch (err: any) {
      finishGeneratingState()
      message.error(err.message || '同步视频任务状态失败')
    } finally {
      taskSyncing = false
    }
  }, 2000)
}

const currentAsset = computed(() => {
  if (activeTask.value && activeTask.value.status === 'success' && activeTask.value.resultAssetId) {
    return assetStore.assets.find(a => a.id === activeTask.value?.resultAssetId)
  }
  return null
})

const getAssetThumbUrl = (assetId?: number) => {
  if (!assetId) return ''
  const asset = assetStore.assets.find(a => a.id === assetId)
  return asset ? asset.thumbUrl : ''
}

// 清除表单
const clearForm = () => {
  form.prompt = ''
  form.modelName = ''
  form.duration = defaultVideoDuration.value
  form.cameraMotion = defaultCameraMotion.value
  form.motionSpeed = 5
  selectedImageAsset.value = null
  selectedEndAsset.value = null
  message.info('表单已重置')
}

const handleModeChange = (val: string) => {
  if (val === 'txt2vid') {
    selectedImageAsset.value = null
  }
}

const handleClearRefImage = () => {
  selectedImageAsset.value = null
  message.info('参考图已移除，降级为文生视频模式')
}

// 打开资产选择弹窗（指定首帧/尾帧模式）
const openAssetPicker = async (mode: 'start' | 'end') => {
  assetPickerMode.value = mode
  await loadAssetLibrary()
  showAssetSelectModal.value = true
}

// 从弹窗选择资产
const handleSelectAssetFromModal = (asset: Asset) => {
  if (assetPickerMode.value === 'start') {
    selectedImageAsset.value = asset
    message.success('首帧已加载！')
  } else {
    selectedEndAsset.value = asset
    message.success('尾帧已加载！')
  }
  showAssetSelectModal.value = false
}

// 清除尾帧
const handleClearEndImage = () => {
  selectedEndAsset.value = null
  message.info('尾帧已清除')
}

// 启动生成
const handleStartGenerate = async () => {
  if (!form.providerId) {
    message.error('请选择一个视频模型提供商！')
    return
  }
  if (!form.prompt) {
    message.error('请输入视频运动提示词描述！')
    return
  }
  if (!form.duration) {
    message.error('未加载到真实视频时长配置，请稍后重试')
    return
  }
  if (!form.cameraMotion) {
    message.error('未加载到真实镜头运动配置，请稍后重试')
    return
  }
  if (videoMode.value === 'img2vid' && !selectedImageAsset.value) {
    message.error('图生视频模式下，必须指定首帧参考图！')
    return
  }
  
  stopTaskPolling()
  activeTaskId.value = null
  generating.value = true
  try {
    const requestPayload = {
      projectId: projectStore.activeProjectId,
      providerId: form.providerId,
      prompt: form.prompt,
      modelName: form.modelName,
      sourceAssetId: selectedImageAsset.value?.id,
      endAssetId: selectedEndAsset.value?.id,
      duration: form.duration,
      options: {
        modelName: form.modelName,
        cameraMotion: form.cameraMotion,
        motionSpeed: form.motionSpeed
      }
    }
    const res = await generationApi.generateVideo({
      projectId: requestPayload.projectId,
      providerId: requestPayload.providerId,
      prompt: requestPayload.prompt,
      modelName: requestPayload.modelName,
      sourceAssetId: requestPayload.sourceAssetId,
      endAssetId: requestPayload.endAssetId,
      duration: requestPayload.duration,
      options: requestPayload.options
    })

    const submittedTask = taskStore.normalizeTask((res as any).data)
    const task = await confirmSubmittedVideoTask(submittedTask, {
      projectId: requestPayload.projectId,
      providerId: requestPayload.providerId,
      prompt: requestPayload.prompt.trim(),
      modelName: normalizeTaskField(requestPayload.modelName),
      sourceAssetId: requestPayload.sourceAssetId,
      endAssetId: requestPayload.endAssetId,
      duration: normalizeTaskField(requestPayload.duration),
      cameraMotion: normalizeTaskField(requestPayload.options.cameraMotion),
      motionSpeed: requestPayload.options.motionSpeed
    })
    taskStore.upsertTask(task)
    activeTaskId.value = task.id

    if (task.status === 'success') {
      await ensureTaskResultAssetLoaded(task)
      finishGeneratingState()
      message.success('视频生成完成')
      assetStore.refresh({ projectId: projectStore.activeProjectId }).catch(() => {})
    } else if (task.status === 'failed') {
      message.error(task.errorMessage || '视频生成失败')
    } else {
      message.info('视频任务已提交，正在持续获取最新生成进度...')
      startTaskPolling(task.id)
    }
  } catch (err: any) {
    message.error(err.message || '生成失败')
  } finally {
    generating.value = false
  }
}

function hasVideoHistoryTask(taskId: number) {
  return taskHistory.value.some(task => task.id === taskId)
}

function ensureVideoHistoryTasksRemoved(taskIds: number[]) {
  const remaining = taskIds.filter(id => hasVideoHistoryTask(id))
  if (remaining.length > 0) {
    throw new Error('视频历史删除结果待确认')
  }
}

const handleDeleteTask = async (taskId: number) => {
  try {
    await taskStore.deleteTask(taskId)
    if (activeTaskId.value === taskId) { activeTaskId.value = null }
    ensureVideoHistoryTasksRemoved([taskId])
    message.success('已删除')
  } catch (err: any) {
    message.error(err.message || '删除失败')
  }
}

const handleBatchClear = async () => {
  const ids = taskHistory.value.filter(t => t.status === 'success' || t.status === 'failed').map(t => t.id)
  if (ids.length === 0) {
    message.info('当前没有可清空的视频历史')
    return
  }
  try {
    for (const id of ids) {
      await taskStore.deleteTask(id)
    }
    activeTaskId.value = null
    ensureVideoHistoryTasksRemoved(ids)
    message.success('历史记录已清空')
  } catch (err: any) {
    message.error(err.message || '清空失败')
  }
}

const handleSelectHistory = (task: any) => {
  activeTaskId.value = task.id
}

const handleInspectHistoryTask = async (task: any) => {
  if (task.status !== 'success' || !task.resultAssetId) {
    message.error(task.errorMessage || '该任务生成失败，没有可查看的视频结果')
    return
  }
  try {
    await ensureTaskResultAssetLoaded(task)
    handleSelectHistory(task)
  } catch (err: any) {
    message.error(err.message || '该任务的视频结果待确认')
  }
}

const handleToggleFavorite = async () => {
  if (currentAsset.value) {
    try {
      const updated = await assetStore.toggleFavorite(currentAsset.value.id)
      await assetStore.refresh({ projectId: projectStore.activeProjectId })
      const confirmed = assetStore.assets.find(asset => asset.id === updated.id)
      if (!confirmed || confirmed.favorite !== updated.favorite) {
        throw new Error('收藏状态待确认')
      }
      message.success(confirmed.favorite ? '视频已收藏！' : '已取消收藏')
    } catch (err: any) {
      message.error(err.message || '收藏状态更新失败')
    }
  }
}

const handleDownload = () => {
  if (currentAsset.value) {
    message.info(`正在打包 ${currentAsset.value.fileName} 高清视频流资源...`)
    const a = document.createElement('a')
    a.href = currentAsset.value.fileUrl
    a.download = currentAsset.value.fileName
    a.target = '_blank'
    a.click()
  }
}

onBeforeUnmount(() => {
  finishGeneratingState()
})
</script>

<style scoped>
.generate-container {
  height: calc(100vh - 120px);
  padding-bottom: 20px;
  color: var(--text-primary);
}

.glass-card {
  background: rgba(15, 23, 42, 0.4) !important;
  backdrop-filter: blur(16px);
  border: 1px solid var(--border-color) !important;
  border-radius: 16px !important;
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.2);
}

.control-card {
  height: 100%;
}

.mode-tabs {
  margin-bottom: 15px;
}

.form-scrollbar {
  flex: 1;
}

.form-status {
  margin: -8px 0 12px;
  font-size: 12px;
  color: #9ca3af;
}

.form-status--error {
  color: #fca5a5;
}

/* 参考图区域 */
.ref-section {
  display: flex;
  flex-direction: column;
  margin-bottom: 20px;
}

.ref-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.ref-label {
  font-size: 13px;
  color: #d1d5db;
  font-weight: 500;
}

.ref-preview-box {
  height: 110px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.2);
  cursor: pointer;
  transition: all 0.3s;
}

.ref-preview-box:hover {
  border-color: rgba(255, 255, 255, 0.25);
}

.ref-preview-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #9ca3af;
  font-size: 11px;
  gap: 8px;
}

.empty-ref-icon {
  width: 24px;
  height: 24px;
  color: rgba(255, 255, 255, 0.3);
}

.ref-preview-active {
  position: relative;
  width: 100%;
  height: 100%;
}

.ref-preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remove-ref-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 8px;
  background: rgba(239, 68, 68, 0.85);
  color: #fff;
  font-size: 10px;
  border-radius: 4px;
  cursor: pointer;
}

/* 时长控制卡 */
.duration-group {
  display: flex;
  gap: 12px;
  width: 100%;
}

.duration-card {
  flex: 1;
  border: 1px solid var(--border-color);
  background: rgba(128, 128, 128, 0.02);
  border-radius: 10px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: all 0.3s;
}

.duration-card:hover {
  border-color: rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.04);
}

.duration-card.active {
  border-color: #f59e0b;
  background: rgba(245, 158, 11, 0.05);
}

.duration-sec {
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 4px;
}

.duration-cost {
  font-size: 10px;
  color: #9ca3af;
}

.duration-card.active .duration-cost {
  color: #f59e0b;
}

.slider-val {
  text-align: right;
  font-size: 12px;
  color: #f59e0b;
  font-weight: 600;
  margin-top: -8px;
}

.btn-area {
  padding-top: 15px;
  border-top: 1px solid var(--border-color);
}

.generate-btn {
  background: linear-gradient(135deg, #f59e0b, #ec4899) !important;
  border: none !important;
  box-shadow: 0 4px 15px rgba(245, 158, 11, 0.3) !important;
  color: #fff !important;
}

.gen-zap {
  width: 16px;
  height: 16px;
  margin-right: 6px;
}

/* 右侧预览区 */
.preview-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  text-align: center;
  padding: 40px;
}

.empty-glow {
  position: absolute;
  width: 250px;
  height: 250px;
  border-radius: 50%;
  z-index: 1;
}

.empty-icon {
  width: 60px;
  height: 60px;
  color: rgba(255, 255, 255, 0.2);
  margin-bottom: 20px;
  z-index: 2;
}

.empty-state h3 {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 10px 0;
  z-index: 2;
}

.empty-state p {
  font-size: 13px;
  max-width: 380px;
  margin: 0;
  z-index: 2;
}

/* 生成中 */
.loading-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.pulsing-glow {
  position: absolute;
  width: 220px;
  height: 220px;
  background: radial-gradient(circle, rgba(245, 158, 11, 0.08) 0%, transparent 70%);
  animation: pulse-light 1.5s infinite alternate;
}

@keyframes pulse-light {
  0% { transform: scale(0.9); opacity: 0.7; }
  100% { transform: scale(1.15); opacity: 1; }
}

.progress-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.progress-inner .pct {
  font-size: 28px;
  font-weight: 800;
}

.progress-inner .time {
  font-size: 10px;
  color: #9ca3af;
}

.progress-pending {
  width: 160px;
  height: 160px;
  border-radius: 50%;
  border: 6px solid rgba(255, 255, 255, 0.05);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
}

.progress-inner--pending .pct {
  font-size: 16px;
}

.loading-info {
  margin-top: 30px;
  text-align: center;
  z-index: 2;
}

.loading-info h3 {
  font-size: 18px;
  color: #fff;
  margin: 0 0 8px 0;
}

.progress-step-text {
  font-size: 13px;
  font-weight: 500;
}

/* 视频渲染结果 */
.result-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-bottom: 120px;
}

.video-wrapper {
  position: relative;
  width: 100%;
  max-height: 480px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4);
  background-color: #05070c;
  display: flex;
  align-items: center;
  justify-content: center;
}

.result-video {
  width: 100%;
  max-height: 450px;
  outline: none;
}

.action-float-bar {
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 30px;
  padding: 6px 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
  z-index: 5;
}

.float-btn {
  background: rgba(255, 255, 255, 0.05) !important;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  color: #fff !important;
}

.favorited {
  color: #ef4444 !important;
  fill: #ef4444 !important;
}

.params-details-card {
  width: 100%;
  margin-top: 16px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 12px;
}

.params-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
}

.model-badge {
  color: #f59e0b;
  font-weight: 600;
}

.date {
  color: #6b7280;
}

.prompt-display {
  font-size: 13px;
  color: #d1d5db;
  line-height: 1.5;
  margin: 0;
}

/* 历史列表 */
.history-section {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  padding: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(10px);
  z-index: 10;
}

.history-status {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  padding: 18px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(15, 23, 42, 0.6);
  color: #fca5a5;
  font-size: 13px;
  text-align: center;
  backdrop-filter: blur(10px);
  z-index: 10;
}

.history-label {
  display: block;
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 10px;
}

.history-row {
  display: flex;
  gap: 12px;
  padding-bottom: 4px;
}

.history-thumb-box {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  transition: all 0.3s;
}

.history-thumb-box:hover {
  transform: translateY(-2px);
  border-color: rgba(255, 255, 255, 0.3);
}

.history-thumb-box.active {
  border-color: #f59e0b;
  box-shadow: 0 0 10px rgba(245, 158, 11, 0.3);
}

.history-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb-failed-overlay {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(127, 29, 29, 0.9), rgba(68, 12, 12, 0.95));
}

.thumb-failed-text {
  font-size: 12px;
  font-weight: 700;
  color: #fecaca;
  letter-spacing: 1px;
}

.thumb-video-badge {
  position: absolute;
  top: 4px;
  left: 4px;
  z-index: 2;
  background: rgba(245, 158, 11, 0.85);
  color: #fff;
  border-radius: 4px;
  padding: 1px 3px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb-badge-icon {
  width: 10px;
  height: 10px;
}

/* 资产挑选网格 */
.assets-picker-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  max-height: 400px;
  overflow-y: auto;
  padding: 10px 0;
}

.picker-item {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.02);
  transition: all 0.3s;
}

.picker-item:hover {
  transform: translateY(-3px);
  border-color: #f59e0b;
}

.picker-thumb {
  width: 100%;
  height: 100px;
  object-fit: cover;
}

.picker-info {
  padding: 8px;
  text-align: center;
}

.picker-name {
  font-size: 11px;
  color: #d1d5db;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.picker-empty {
  grid-column: span 4;
  text-align: center;
  padding: 40px;
  color: #9ca3af;
  font-size: 13px;
}

/* ---- 双帧预览 ---- */
.dual-frame-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 12px;
  margin-bottom: 14px;
  background: rgba(0, 0, 0, 0.15);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.frame-box {
  width: 80px;
  height: 80px;
  border-radius: 10px;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.08);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.3s;
}

.frame-box.frame-active {
  border-color: #10b981;
}

.frame-label {
  position: absolute;
  top: 2px;
  left: 4px;
  font-size: 9px;
  color: #fff;
  font-weight: 600;
  text-shadow: 0 0 4px rgba(0,0,0,0.8);
  z-index: 1;
}

.frame-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.frame-placeholder {
  font-size: 10px;
  color: #6b7280;
}

.frame-arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.arrow-icon {
  width: 24px;
  height: 24px;
  color: #f59e0b;
}

.arrow-text {
  font-size: 9px;
  color: #6b7280;
}

/* 双帧选择器行 */
.ref-picker-row {
  display: flex;
  gap: 12px;
}

.ref-picker-block {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ref-label {
  font-size: 12px;
  color: #9ca3af;
  font-weight: 500;
}

.ref-preview-box.mini {
  height: 70px;
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
  border: 1px dashed rgba(255, 255, 255, 0.12);
  transition: border-color 0.3s;
}

.ref-preview-box.mini:hover {
  border-color: #10b981;
}

.ref-preview-active.mini {
  width: 100%;
  height: 100%;
  position: relative;
}

.ref-preview-empty.mini {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 10px;
  color: #6b7280;
  gap: 4px;
}

.empty-ref-icon.small {
  width: 18px;
  height: 18px;
  color: #6b7280;
}

.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.thumb-remove {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(239,68,68,0.85);
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity .2s;
  z-index: 5;
}

.history-thumb-box:hover .thumb-remove {
  opacity: 1;
}
</style>
