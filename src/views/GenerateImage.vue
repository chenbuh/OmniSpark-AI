<template>
  <div ref="pageRoot" class="generate-container">
    <n-row :gutter="24" style="height: 100%;">
      <!-- 左侧参数设置区 -->
      <n-col :span="9" style="height: 100%;">
        <n-card class="glass-card control-card" content-style="display: flex; flex-direction: column; height: 100%;">
          <div class="card-orb card-orb-left"></div>
          <n-tabs v-model:value="generateMode" type="line" justify-content="space-evenly" class="mode-tabs" @update:value="handleModeChange">
            <n-tab-pane name="txt2img" tab="文生图" />
            <n-tab-pane name="img2img" tab="图生图" />
            <n-tab-pane name="inpaint" tab="局部重绘" />
          </n-tabs>

          <n-scrollbar class="form-scrollbar">
            <n-form label-placement="top" size="medium" style="padding-right: 8px;">
              <!-- 模型提供商 -->
              <n-form-item label="模型提供商">
                <n-select
                  v-model:value="form.providerId"
                  :options="providerOptions"
                  placeholder="选择已配置的提供商..."
                  @update:value="handleProviderChange"
                />
              </n-form-item>

              <!-- 基础模型 -->
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
                模型提供商待确认，请稍后重试。
              </div>
              <div v-else-if="providerLoadState === 'ready' && providerOptions.length === 0" class="form-status">
                当前项目暂无可用生图提供商，请先前往模型配置。
              </div>
              <div v-if="metaLoadState === 'error'" class="form-status form-status--error">
                生图分辨率与质量配置待确认，请稍后重试。
              </div>

              <!-- 提示词输入 -->
              <n-form-item label="提示词指令 (Prompt)">
                <div class="prompt-input-box">
                  <n-input
                    v-model:value="form.prompt"
                    type="textarea"
                    :autosize="{ minRows: 3, maxRows: 6 }"
                    placeholder="输入您构想的画面描述，支持中英文... (例如: 赛博朋克霓虹街道)"
                  />
                  <n-button
                    size="tiny"
                    type="primary"
                    secondary
                    class="optimize-btn"
                    :loading="optimizing"
                    title="调用项目内已配置的 OpenAI / Custom 文本模型，真实润色提示词"
                    @click="handleOptimizePrompt"
                  >
                    <Sparkles class="optimize-icon" /> 提示词润色
                  </n-button>
                </div>
              </n-form-item>

              <!-- 负向提示词 -->
              <n-form-item label="排除要素 (Negative Prompt)">
                <n-input
                  v-model:value="form.negativePrompt"
                  type="textarea"
                  :autosize="{ minRows: 1.5, maxRows: 3 }"
                  placeholder="输入不想出现在画面中的元素 (例如: 模糊, 变形, 低画质)"
                />
              </n-form-item>

              <!-- 图生图参考图 (多图) -->
              <div v-if="generateMode === 'img2img'" class="ref-image-section">
                <span class="ref-label">参考图像 ({{ selectedRefAssets.filter(Boolean).length }}/16)</span>
                <div class="ref-grid">
                  <div v-for="(ref, idx) in selectedRefAssets" :key="idx" class="ref-thumb-box">
                    <img v-if="ref" :src="(typeof ref === 'string' ? ref : ref.thumbUrl || ref.fileUrl)" alt="参考图" class="ref-thumb-img" />
                    <div class="ref-thumb-remove" @click="removeRef(idx)">×</div>
                  </div>
                  <div class="ref-add-box" @click="handleTriggerUpload" @dragover.prevent @drop.prevent="handleDrop">
                    <UploadCloud class="upload-cloud-icon" />
                    <span>添加参考图</span>
                  </div>
                </div>
                <div class="ref-hint">支持多张参考图，拖拽或点击上传 JPG/PNG</div>
                <input type="file" ref="fileInput" class="hidden-input" accept="image/*" multiple @change="handleFileChange" />
              </div>

              <!-- 局部重绘区域 -->
              <div v-if="generateMode === 'inpaint'" class="ref-image-section">
                <span class="ref-label">原始参考图 + 绘制遮罩</span>
                <div class="inpaint-canvas-wrapper" v-if="selectedRefAssets[0]">
                  <div class="inpaint-canvas-container">
                    <img :src="typeof selectedRefAssets[0] === 'string' ? selectedRefAssets[0] : selectedRefAssets[0]?.thumbUrl || selectedRefAssets[0]?.fileUrl" alt="局部重绘原图" class="inpaint-bg-img" ref="inpaintBgImg" @load="initInpaintCanvas" />
                    <canvas
                      ref="inpaintCanvas"
                      class="inpaint-overlay-canvas"
                      @mousedown="startInpaintDraw"
                      @mousemove="doInpaintDraw"
                      @mouseup="stopInpaintDraw"
                      @mouseleave="stopInpaintDraw"
                    ></canvas>
                  </div>
                  <div class="inpaint-toolbar">
                    <n-space align="center" :size="12">
                      <span class="toolbar-label">笔刷大小:</span>
                      <n-slider v-model:value="inpaintBrushSize" :min="5" :max="80" :step="1" style="width: 100px;" />
                      <span class="brush-size-val">{{ inpaintBrushSize }}px</span>
                    </n-space>
                    <n-space>
                      <n-button size="tiny" :type="inpaintMode === 'draw' ? 'primary' : 'default'" secondary @click="inpaintMode = 'draw'">
                        <template #icon><PenTool /></template>绘制
                      </n-button>
                      <n-button size="tiny" :type="inpaintMode === 'erase' ? 'warning' : 'default'" secondary @click="inpaintMode = 'erase'">
                        <template #icon><Eraser /></template>擦除
                      </n-button>
                      <n-button size="tiny" type="error" secondary @click="resetInpaintMask">
                        <template #icon><RotateCcw /></template>重置
                      </n-button>
                    </n-space>
                  </div>
                </div>
                <div v-else class="upload-drag-area" @click="handleTriggerUpload">
                  <div class="upload-placeholder">
                    <UploadCloud class="upload-cloud-icon" />
                    <span>点击上传要重绘的原始图片</span>
                    <span class="upload-tip">然后绘制遮罩标记要重绘的区域</span>
                  </div>
                </div>
                <input type="file" ref="fileInput" class="hidden-input" accept="image/*" @change="handleFileChange" />
              </div>

              <!-- 比例设置 -->
              <n-form-item label="画面宽高比">
                <div class="aspect-ratio-group">
                  <div
                    v-for="item in aspectRatios"
                    :key="item.value"
                    class="ratio-card"
                    :class="[
                      { 'active': form.aspectRatio === item.value },
                      item.value === 'custom' ? 'custom-ratio-card' : ''
                    ]"
                    @click="form.aspectRatio = item.value"
                  >
                    <div class="shape-container">
                      <div class="ratio-shape" :class="'ratio-' + item.value"></div>
                    </div>
                    <span class="ratio-label">{{ item.label }}</span>
                  </div>
                </div>
                <div v-if="form.aspectRatio === 'custom'" class="custom-config-inputs">
                  <div class="custom-input-item">
                    <span class="custom-input-label">宽度比例 (Width)</span>
                    <n-input-number v-model:value="form.ratioWidth" :min="1" :max="32" :step="1" placeholder="宽" />
                  </div>
                  <div class="custom-input-sep">×</div>
                  <div class="custom-input-item">
                    <span class="custom-input-label">高度比例 (Height)</span>
                    <n-input-number v-model:value="form.ratioHeight" :min="1" :max="32" :step="1" placeholder="高" />
                  </div>
                </div>
              </n-form-item>

              <n-form-item label="输出分辨率">
                <n-select
                  v-model:value="form.resolution"
                  :options="resolutionOptions"
                  placeholder="选择输出分辨率"
                />
                <div v-if="form.resolution === 'custom'" class="custom-config-inputs">
                  <div class="custom-input-item">
                    <span class="custom-input-label">画面宽度 (Width px)</span>
                    <n-input-number v-model:value="form.customWidth" :min="256" :max="4096" :step="64" placeholder="宽度(px)" />
                  </div>
                  <div class="custom-input-sep">×</div>
                  <div class="custom-input-item">
                    <span class="custom-input-label">画面高度 (Height px)</span>
                    <n-input-number v-model:value="form.customHeight" :min="256" :max="4096" :step="64" placeholder="高度(px)" />
                  </div>
                </div>
                <div class="config-hint">
                  当前输出尺寸：{{ resolvedSizeLabel }}
                </div>
              </n-form-item>

              <n-form-item label="生成质量">
                <n-select
                  v-model:value="form.quality"
                  :options="qualityOptions"
                  placeholder="选择生成质量"
                />
              </n-form-item>

              <!-- 高级参数折叠 -->
              <n-collapse style="margin-top: 15px;">
                <n-collapse-item title="高级生成参数配置" name="1">
                  <n-form-item label="生成张数 (Batch Count)">
                    <n-input-number v-model:value="form.count" :min="1" :max="8" :step="1" />
                    <div class="config-hint">支持 1 到 8 张批量生成</div>
                  </n-form-item>
                  <n-form-item label="引导系数 (CFG Scale)">
                    <n-slider v-model:value="form.cfg" :min="1.0" :max="20.0" :step="0.5" />
                    <div class="slider-val">{{ form.cfg.toFixed(1) }}</div>
                  </n-form-item>
                  <n-form-item label="渲染步数 (Steps)">
                    <n-slider v-model:value="form.steps" :min="10" :max="50" :step="1" />
                    <div class="slider-val">{{ form.steps }} 步</div>
                  </n-form-item>
                </n-collapse-item>
              </n-collapse>
            </n-form>
          </n-scrollbar>

          <div class="btn-area">
            <n-space style="width:100%;">
              <n-button type="primary" block size="large" class="generate-btn" :loading="generating" @click="handleStartGenerate">
                <Zap class="gen-zap" /> 立即构建画面
              </n-button>
            </n-space>
            <div style="text-align:center;margin-top:6px;">
              <n-button size="tiny" quaternary @click="clearForm">重置表单</n-button>
            </div>
          </div>
        </n-card>
      </n-col>

      <!-- 右侧工作预览区 -->
      <n-col :span="15" style="height: 100%;">
        <n-card class="glass-card preview-card">
          <div class="card-orb card-orb-right"></div>
          <!-- 1. 生成中状态 (真实进度) -->
          <div v-if="!taskCompleted && (generating || (activeTask && (activeTask.status === 'pending' || activeTask.status === 'running')))" class="loading-state">
            <div class="pulsing-glow"></div>
            <n-progress
              v-if="activeTaskProgress !== null"
              type="circle"
              :percentage="activeTaskProgress"
              :color="'#10b981'"
              :rail-color="'rgba(255, 255, 255, 0.05)'"
              :stroke-width="6"
              :width="160"
            >
              <template #default>
                <div class="progress-inner">
                  <span class="pct">{{ activeTaskProgress }}%</span>
                  <span class="time">{{ formatElapsed(runningElapsed) }}</span>
                </div>
              </template>
            </n-progress>
            <div v-else class="progress-pending">
              <n-spin size="large" />
              <div class="progress-inner progress-inner--pending">
                <span class="pct">{{ submitting ? '提交中' : '待同步' }}</span>
                <span class="time">{{ submitting ? '正在提交任务...' : formatElapsed(runningElapsed) }}</span>
              </div>
            </div>
            <div class="loading-info">
              <h3>{{ submitting ? '正在提交任务...' : '画面构图渲染中...' }}</h3>
              <p class="progress-step-text">{{ submitting ? '正在向模型服务提交生成请求' : (activeTask?.progressText || '等待进度更新...') }}</p>
            </div>
          </div>

          <!-- 2. 生成成功结果展示 -->
          <div v-else-if="currentAsset" class="result-state">
            <div class="image-wrapper">
              <img :src="currentAsset.fileUrl" :alt="actualImagePrompt || '生成结果图片'" class="result-img" @click="showLightbox = true" />
              <!-- 批量切换指示 -->
              <div class="batch-indicator" v-if="batchTotal > 1">
                <n-button size="tiny" secondary :disabled="selectedBatchIndex <= 0" @click.stop="selectedBatchIndex--">
                  <template #icon><ChevronLeft /></template>
                </n-button>
                <span class="batch-count">{{ selectedBatchIndex + 1 }} / {{ batchTotal }}</span>
                <n-button size="tiny" secondary :disabled="selectedBatchIndex >= batchTotal - 1" @click.stop="selectedBatchIndex++">
                  <template #icon><ChevronRight /></template>
                </n-button>
              </div>
            </div>
            <!-- 控制悬浮条（独立于图片容器，避免被裁切） -->
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
              <n-button type="warning" size="small" round class="float-action-btn" @click="handleToVideo">
                <template #icon>
                  <Tv />
                </template>
                一键生成视频
              </n-button>
              <n-button type="success" size="small" round class="float-action-btn" @click="handleShareToCommunity">
                <template #icon>
                  <Globe />
                </template>
                分享到社区
              </n-button>
            </div>

            <!-- 批量结果缩略图网格 -->
            <div class="batch-thumbnails" v-if="batchTotal > 1">
              <div
                v-for="(asset, idx) in currentAssets"
                :key="asset.id"
                class="batch-thumb-item"
                :class="{ 'active': idx === selectedBatchIndex }"
                @click="selectedBatchIndex = idx"
              >
                <img :src="asset.thumbUrl" :alt="actualImagePrompt || getImageTaskDisplayPrompt(activeTask) || '批次图片缩略图'" class="batch-thumb-img" loading="lazy" />
                <div class="batch-thumb-overlay">
                  <span class="batch-thumb-idx">{{ idx + 1 }}</span>
                </div>
              </div>
            </div>

            <div class="params-details-card">
              <div class="params-head">
                <span class="model-badge">模型: {{ actualImageModelName || '未记录模型' }}</span>
                <span class="date">{{ currentAsset.createdAt }}</span>
                <span class="batch-badge" v-if="batchTotal > 1">共 {{ batchTotal }} 张</span>
              </div>
              <p class="prompt-display"><strong>Prompt:</strong> {{ actualImagePrompt || '待确认' }}</p>
              <p v-if="actualImageNegativePrompt" class="prompt-display prompt-display-negative"><strong>Negative:</strong> {{ actualImageNegativePrompt }}</p>
              <!-- 实际参数对比 -->
              <n-collapse style="margin-top:10px;">
                <n-collapse-item title="📊 实际生成参数" name="params">
                  <div class="param-compare-grid">
                    <div class="param-item">
                      <span class="param-label">尺寸</span>
                      <span class="param-value">{{ actualImageSizeLabel }}</span>
                    </div>
                    <div class="param-item" v-if="actualImageCfgLabel">
                      <span class="param-label">CFG</span>
                      <span class="param-value">{{ actualImageCfgLabel }}</span>
                    </div>
                    <div class="param-item" v-if="actualImageStepsLabel">
                      <span class="param-label">Steps</span>
                      <span class="param-value">{{ actualImageStepsLabel }}</span>
                    </div>
                    <div class="param-item">
                      <span class="param-label">质量</span>
                      <span class="param-value">{{ actualImageQualityLabel }}</span>
                    </div>
                    <div class="param-item">
                      <span class="param-label">生成模式</span>
                      <span class="param-value">{{ actualImageModeLabel }}</span>
                    </div>
                    <div class="param-item" v-if="actualImageModelName">
                      <span class="param-label">模型</span>
                      <span class="param-value">{{ actualImageModelName }}</span>
                    </div>
                    <div class="param-item" v-if="actualImageBatchCountLabel">
                      <span class="param-label">张数</span>
                      <span class="param-value">{{ actualImageBatchCountLabel }}</span>
                    </div>
                  </div>
                </n-collapse-item>
              </n-collapse>
            </div>
          </div>

          <!-- 3. 初始默认空状态 -->
          <div v-else class="empty-state">
            <div class="empty-glow"></div>
            <Paintbrush class="empty-icon" />
            <h3>{{ emptyStateTitle }}</h3>
            <p>{{ emptyStateDescription }}</p>
          </div>

          <!-- 历史大图列表 -->
          <div class="history-section" v-if="historyLoadState === 'ready' && taskHistory.length > 0">
            <div class="history-head">
              <span class="history-label">当前项目生成历史 ({{ taskHistory.length }})</span>
              <n-space>
                <n-button size="tiny" quaternary @click="showHistoryModal = true">查看全部</n-button>
                <n-button size="tiny" type="error" tertiary @click="handleBatchClear">清空</n-button>
              </n-space>
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
                  <img v-else-if="task.status === 'success'" :src="getTaskThumbUrl(task)" :alt="getImageTaskDisplayPrompt(task) || '历史任务缩略图'" class="history-thumb-img" />
                  <div v-else class="thumb-failed-overlay">
                    <span class="thumb-failed-text">失败</span>
                  </div>
                  <div class="thumb-remove" @click.stop="handleDeleteTask(task.id)">×</div>
                </div>
              </div>
            </n-scrollbar>
          </div>
          <div v-else-if="historyLoadState === 'error'" class="history-status">
            生成历史待确认，请稍后重试。
          </div>
        </n-card>
      </n-col>
    </n-row>

    <!-- 历史记录弹窗 (画廊模式) -->
    <n-modal v-model:show="showHistoryModal" preset="card" title="生成历史记录" style="width: 85vw; max-width: 1100px;" closable>
      <div class="gallery-toolbar">
        <n-tabs v-model:value="historyFilter" type="segment" class="history-tabs">
          <n-tab name="all">全部 ({{ taskHistory.length }})</n-tab>
          <n-tab name="success">成功</n-tab>
          <n-tab name="failed">失败</n-tab>
        </n-tabs>
        <n-button size="tiny" type="error" tertiary @click="handleBatchClear">清空全部</n-button>
      </div>
      <div class="gallery-grid" v-if="filteredHistory.length > 0">
        <div v-for="task in filteredHistory" :key="task.id" class="gallery-card"
          :class="{ 'gallery-active': activeTask?.id === task.id }">
          <div class="gallery-preview" @click="handleInspectHistoryTask(task); showHistoryModal=false">
            <div v-if="task.status === 'running' || task.status === 'pending'" class="gallery-loading">
              <n-spin size="small" />
              <span class="gallery-loading-text">进行中</span>
            </div>
            <img v-else-if="task.status === 'success'"
              :src="getTaskThumbUrl(task)" :alt="getImageTaskDisplayPrompt(task) || '生成结果缩略图'" class="gallery-img" loading="lazy" />
            <div v-else class="gallery-failed">
              <span>❌ 失败</span>
            </div>
          </div>
          <div class="gallery-meta">
            <n-ellipsis :line-clamp="1" class="gallery-prompt">{{ getImageTaskDisplayPrompt(task) || '无提示词' }}</n-ellipsis>
            <div class="gallery-bottom">
              <span class="gallery-date">{{ String(task.createdAt||'').replace('T',' ').substring(5,16) }}</span>
              <n-space :size="4">
                <n-button size="tiny" quaternary @click="handleInspectHistoryTask(task); showHistoryModal=false">
                  <template #icon><Eye /></template>
                </n-button>
                <n-button size="tiny" type="error" tertiary @click="handleDeleteTask(task.id)">
                  <template #icon><Trash2 /></template>
                </n-button>
              </n-space>
            </div>
          </div>
        </div>
      </div>
      <n-empty
        v-else-if="historyLoadState === 'error'"
        description="历史记录待确认，请稍后重试"
      />
      <n-empty
        v-else
        :description="historyLoadState === 'loading' ? '正在加载历史记录...' : '暂无记录'"
      />
    </n-modal>

    <!-- 灯箱弹窗 -->
    <n-modal v-model:show="showLightbox" preset="card" class="lightbox-modal" style="width: 80vw; max-width: 1000px; background: transparent;">
      <div class="lightbox-content">
        <img :src="currentAsset?.fileUrl" :alt="currentAsset?.prompt || '图片大图预览'" class="lightbox-img" />
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { gsap } from 'gsap'
import { useProjectStore } from '@/store/project'
import { useModelProviderStore } from '@/store/provider'
import { useTaskStore } from '@/store/task'
import { useAssetStore, type Asset } from '@/store/asset'
import { assetApi } from '@/api/assets'
import { generationApi, type GenerationMetaOption, type GenerationMetaVO, type PromptOptimizeResult } from '@/api/generation'
import { taskApi } from '@/api/tasks'
import {
  Sparkles,
  Zap,
  Paintbrush,
  Download,
  Heart,
  Tv,
  UploadCloud,
  ChevronLeft,
  ChevronRight,
  PenTool,
  Eraser,
  RotateCcw,
  Globe,
  Eye,
  Trash2
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const pageRoot = ref<HTMLElement | null>(null)

const projectStore = useProjectStore()
const providerStore = useModelProviderStore()
const taskStore = useTaskStore()
const assetStore = useAssetStore()

const generateMode = ref('txt2img')
const optimizing = ref(false)
const generating = ref(false)
const submitting = ref(false)
const taskCompleted = ref(false)
const elapsedTick = ref(0)
let elapsedTimer: ReturnType<typeof setInterval> | null = null
const showLightbox = ref(false)
const refImagePreviews = ref<string[]>([])
const selectedRefAssets = ref<(Asset | string)[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

// 局部重绘状态
const inpaintCanvas = ref<HTMLCanvasElement | null>(null)
const inpaintBgImg = ref<HTMLImageElement | null>(null)
const inpaintBrushSize = ref(20)
const inpaintMode = ref<'draw' | 'erase'>('draw')
const inpaintDrawing = ref(false)
const inpaintMaskAssetId = ref<number | null>(null)

// 当前页面激活选择的任务
const activeTaskId = ref<number | null>(null)
const selectedBatchIndex = ref(0)
const showHistoryModal = ref(false)
const historyFilter = ref('all')
const generationMeta = ref<GenerationMetaVO>({})
const metaLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const providerLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const historyLoadState = ref<'loading' | 'ready' | 'error'>('loading')
let taskPollingTimer: ReturnType<typeof setInterval> | null = null
let taskSyncing = false
const resultVersion = ref(0)
let entranceMatchMedia: gsap.MatchMedia | null = null

const filteredHistory = computed(() => {
  let list = taskHistory.value
  if (historyFilter.value !== 'all') {
    list = list.filter(t => t.status === historyFilter.value)
  }
  return list
})

const form = reactive({
  providerId: null as number | null,
  modelName: '',
  prompt: '',
  negativePrompt: '',
  aspectRatio: '1-1',
  ratioWidth: 1,
  ratioHeight: 1,
  resolution: '',
  customWidth: 1024,
  customHeight: 1024,
  quality: '',
  count: 1,
  cfg: 7.5,
  steps: 25
})

const runningElapsed = computed(() => {
  void elapsedTick.value
  if (!activeTask.value?.createdAt) return 0
  const start = Date.parse(String(activeTask.value.createdAt).replace(' ','T'))
  if (!start) return 0
  return Math.floor((Date.now() - start) / 1000)
})

const aspectRatios = [
  { label: '1:1 正方形', value: '1-1' },
  { label: '4:3 摄影构图', value: '4-3' },
  { label: '3:4 海报竖构图', value: '3-4' },
  { label: '3:2 单反横构图', value: '3-2' },
  { label: '2:3 竖版摄影', value: '2-3' },
  { label: '16:9 电脑宽屏', value: '16-9' },
  { label: '9:16 手机竖屏', value: '9-16' },
  { label: '21:9 电影宽银幕', value: '21-9' },
  { label: '9:21 超长竖屏', value: '9-21' },
  { label: '自定义比例', value: 'custom' }
]

const resolutionOptions = computed(() => generationMeta.value.image?.resolutionOptions || [])
const qualityOptions = computed(() => generationMeta.value.image?.qualityOptions || [])
const defaultImageResolution = computed(() => generationMeta.value.image?.defaults?.resolution || resolutionOptions.value[0]?.value || '')
const defaultImageQuality = computed(() => generationMeta.value.image?.defaults?.quality || qualityOptions.value[0]?.value || '')

const allowedImageProviderTypes = computed(() => generationMeta.value.image?.allowedProviderTypes || [])

const resolutionBaseMap: Record<'1k' | '2k' | '4k', number> = {
  '1k': 1024,
  '2k': 2048,
  '4k': 4096
}
type ImageHistoryTask = ReturnType<typeof taskStore.normalizeTask>

function resolveOptionValue(options: GenerationMetaOption[], preferredValue?: string, fallbackValue = '') {
  if (preferredValue && options.some(option => option.value === preferredValue)) return preferredValue
  return options[0]?.value || fallbackValue
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string) {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return fallback
}

function requireGenerationMetaOptionList(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('生图配置待确认')
  }
  const seenValues = new Set<string>()
  return value.map((item: unknown) => {
    if (!isPlainObject(item)) {
      throw new Error('生图配置待确认')
    }
    const label = typeof item.label === 'string' ? item.label.trim() : ''
    const optionValue = typeof item.value === 'string' ? item.value.trim() : ''
    if (!label || !optionValue || seenValues.has(optionValue)) {
      throw new Error('生图配置待确认')
    }
    seenValues.add(optionValue)
    return { label, value: optionValue }
  })
}

function requireAllowedProviderTypes(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('生图配置待确认')
  }
  return Array.from(new Set(
    value.map((item: unknown) => typeof item === 'string' ? item.trim() : '').filter(Boolean)
  ))
}

function requireGenerationMeta(value: unknown): GenerationMetaVO {
  if (!isPlainObject(value)) {
    throw new Error('生图配置待确认')
  }
  const image = value.image
  if (!isPlainObject(image)) {
    throw new Error('生图配置待确认')
  }
  const resolutionOptions = requireGenerationMetaOptionList(image.resolutionOptions)
  const qualityOptions = requireGenerationMetaOptionList(image.qualityOptions)
  const allowedProviderTypes = requireAllowedProviderTypes(image.allowedProviderTypes)
  const defaults = image.defaults
  if (defaults != null && !isPlainObject(defaults)) {
    throw new Error('生图配置待确认')
  }
  return {
    image: {
      allowedProviderTypes,
      resolutionOptions,
      qualityOptions,
      defaults: {
        resolution: typeof defaults?.resolution === 'string' ? defaults.resolution.trim() : '',
        quality: typeof defaults?.quality === 'string' ? defaults.quality.trim() : ''
      }
    }
  }
}

function normalizeImageAssetList(value: unknown, errorMessage: string) {
  if (!Array.isArray(value)) {
    throw new Error(errorMessage)
  }
  const seenIds = new Set<number>()
  return value.map((item: unknown) => {
    const normalized = assetStore.normalizeAsset(item)
    if (seenIds.has(normalized.id)) {
      throw new Error(errorMessage)
    }
    seenIds.add(normalized.id)
    return normalized
  })
}

function requirePromptOptimizeResult(value: unknown): PromptOptimizeResult {
  if (!isPlainObject(value)) {
    throw new Error('提示词润色接口未返回有效结果')
  }
  const prompt = typeof value.prompt === 'string' ? value.prompt.trim() : ''
  if (!prompt) {
    throw new Error('提示词润色接口未返回有效结果')
  }
  const providerId = value.providerId == null ? undefined : Number(value.providerId)
  if (providerId != null && (!Number.isFinite(providerId) || providerId <= 0)) {
    throw new Error('提示词润色接口未返回有效结果')
  }
  return {
    prompt,
    providerId,
    providerName: typeof value.providerName === 'string' ? value.providerName.trim() : undefined,
    modelName: typeof value.modelName === 'string' ? value.modelName.trim() : undefined
  }
}

async function loadGenerationMeta() {
  metaLoadState.value = 'loading'
  try {
    const response = await generationApi.getMeta()
    generationMeta.value = requireGenerationMeta(getResponseData(response, '生图配置待确认'))
    metaLoadState.value = 'ready'
  } catch {
    generationMeta.value = {}
    metaLoadState.value = 'error'
  }
  form.resolution = resolveOptionValue(resolutionOptions.value, form.resolution, defaultImageResolution.value)
  form.quality = resolveOptionValue(qualityOptions.value, form.quality, defaultImageQuality.value)
}

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

async function loadPageContext() {
  const projectId = projectStore.activeProjectId
  await Promise.allSettled([
    loadGenerationMeta(),
    loadProviders(),
    loadTaskHistory(),
    projectId ? assetStore.refresh({ projectId }) : Promise.resolve()
  ])
  initDefaults()
  if (activeTask.value?.status === 'success') {
    void ensureTaskAssetsLoaded(activeTask.value).catch(() => {})
  }
}

const sanitizeInteger = (
  value: number | null | undefined,
  fallback: number,
  min: number,
  max: number
) => {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return fallback
  }
  return Math.min(max, Math.max(min, Math.round(value)))
}

const alignPixelDimension = (value: number) => {
  const normalized = Math.round(value / 64) * 64
  return Math.min(4096, Math.max(256, normalized))
}

const getAspectRatioDimensions = () => {
  if (form.aspectRatio === 'custom') {
    const width = sanitizeInteger(form.ratioWidth, 1, 1, 32)
    const height = sanitizeInteger(form.ratioHeight, 1, 1, 32)
    return {
      width,
      height,
      label: `${width}:${height}`
    }
  }

  const [widthText, heightText] = form.aspectRatio.split('-')
  const width = sanitizeInteger(Number(widthText), 1, 1, 64)
  const height = sanitizeInteger(Number(heightText), 1, 1, 64)
  return {
    width,
    height,
    label: `${width}:${height}`
  }
}

const getResolvedImageSize = () => {
  if (form.resolution === 'custom') {
    return {
      width: alignPixelDimension(sanitizeInteger(form.customWidth, 1024, 256, 4096)),
      height: alignPixelDimension(sanitizeInteger(form.customHeight, 1024, 256, 4096))
    }
  }

  const { width: ratioWidth, height: ratioHeight } = getAspectRatioDimensions()
  const targetLongEdge = resolutionBaseMap[form.resolution as keyof typeof resolutionBaseMap] ?? 1024

  if (ratioWidth >= ratioHeight) {
    return {
      width: targetLongEdge,
      height: alignPixelDimension((targetLongEdge * ratioHeight) / ratioWidth)
    }
  }

  return {
    width: alignPixelDimension((targetLongEdge * ratioWidth) / ratioHeight),
    height: targetLongEdge
  }
}

const resolvedSizeLabel = computed(() => {
  const { width, height } = getResolvedImageSize()
  return `${width} x ${height}px`
})

// 获取当前项目已配置的生图提供商
const providerOptions = computed(() => {
  return providerStore
    .getProvidersForProject(projectStore.activeProjectId)
    .filter(p => allowedImageProviderTypes.value.includes(p.type))
    .map(p => ({
      label: p.name,
      value: p.id
    }))
})

const selectedProvider = computed(() => {
  return providerStore.providers.find(provider => provider.id === form.providerId) || null
})

// 模型列表选择
const modelOptions = ref<{ label: string; value: string }[]>([])

// 获取当前项目生图历史记录
const taskHistory = computed(() => {
  return taskStore
    .getTasksByProject(projectStore.activeProjectId)
    .filter(t => t.taskType === 'image')
})

// 监听项目切换，自动设置默认的模型提供商与模型
const initDefaults = () => {
  const providers = providerStore
    .getProvidersForProject(projectStore.activeProjectId)
    .filter(p => allowedImageProviderTypes.value.includes(p.type))
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
    return '生成配置待确认'
  }
  if (generationConfigState.value === 'empty') {
    return '暂无可用生图模型'
  }
  return '开始您的视觉艺术构想'
})

const emptyStateDescription = computed(() => {
  if (generationConfigState.value === 'error') {
    return '当前项目的生图模型或参数配置暂时无法确认，请稍后重试。'
  }
  if (generationConfigState.value === 'empty') {
    return '当前项目还没有配置与生图兼容的提供商，先去模型配置页接入后再开始创作。'
  }
  return '在左侧配置模型并键入您的创意描述，高质感 AI 画面将在此瞬间为您呈现。'
})

// 清除表单
const clearForm = () => {
  form.prompt = ''
  form.negativePrompt = ''
  form.modelName = ''
  form.count = 1
  form.cfg = 7.5
  form.steps = 25
  form.quality = defaultImageQuality.value
  form.aspectRatio = '1-1'
  form.resolution = defaultImageResolution.value
  selectedRefAssets.value = []
  refImagePreviews.value = []
  taskCompleted.value = true
  stopElapsedTimer()
  inpaintMaskAssetId.value = null
  message.info('表单已重置')
}

// 切换生成模式时重置状态
const handleModeChange = () => {
  inpaintMaskAssetId.value = null
  selectedBatchIndex.value = 0
  stopElapsedTimer()
}

const shouldReduceMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

const getScopedElements = (selector: string) => {
  return Array.from(pageRoot.value?.querySelectorAll<HTMLElement>(selector) ?? [])
}

const getScopedElement = (selector: string) => {
  return pageRoot.value?.querySelector<HTMLElement>(selector) ?? null
}

const animateActiveSelection = async (selector: string) => {
  await nextTick()
  const element = getScopedElement(selector)
  if (!element || shouldReduceMotion()) return

  gsap.killTweensOf(element)
  gsap.fromTo(
    element,
    { scale: 0.95, y: 4 },
    {
      scale: 1,
      y: 0,
      duration: 0.36,
      ease: 'back.out(1.7)',
      clearProps: 'transform'
    }
  )
}

const animateModeSurface = async () => {
  await nextTick()
  if (shouldReduceMotion()) return

  const modeSections = getScopedElements('.ref-image-section, .upload-drag-area, .inpaint-canvas-wrapper, .custom-config-inputs')
  if (modeSections.length > 0) {
    gsap.killTweensOf(modeSections)
    gsap.fromTo(
      modeSections,
      { autoAlpha: 0, y: 14 },
      {
        autoAlpha: 1,
        y: 0,
        duration: 0.42,
        stagger: 0.06,
        ease: 'power2.out',
        clearProps: 'transform,opacity,visibility'
      }
    )
  }

  const button = getScopedElement('.generate-btn')
  if (button) {
    gsap.killTweensOf(button)
    gsap.fromTo(
      button,
      { scale: 0.985 },
      {
        scale: 1,
        duration: 0.3,
        ease: 'power2.out',
        clearProps: 'transform'
      }
    )
  }
}

const animateHistoryRail = async (fromStart = true) => {
  await nextTick()
  const historySection = getScopedElement('.history-section')
  if (!historySection) return
  if (shouldReduceMotion()) {
    gsap.set(historySection, { clearProps: 'transform,opacity,visibility' })
    return
  }

  const thumbs = getScopedElements('.history-thumb-box')
  gsap.killTweensOf([historySection, ...thumbs])

  const tl = gsap.timeline({ defaults: { ease: 'power2.out' } })
  tl.fromTo(
    historySection,
    { autoAlpha: 0, y: 18 },
    {
      autoAlpha: 1,
      y: 0,
      duration: 0.42,
      clearProps: 'transform,opacity,visibility'
    }
  )

  if (thumbs.length > 0) {
    tl.fromTo(
      thumbs,
      { autoAlpha: 0, y: 12, scale: 0.96 },
      {
        autoAlpha: 1,
        y: 0,
        scale: 1,
        duration: 0.32,
        stagger: { each: 0.035, from: fromStart ? 'start' : 'end' },
        clearProps: 'transform,opacity,visibility'
      },
      0.08
    )
  }
}

const animateResultImageSwap = async () => {
  await nextTick()
  if (shouldReduceMotion() || !currentAsset.value) return

  const image = getScopedElement('.result-img')
  const badge = getScopedElement('.batch-indicator')
  if (image) {
    gsap.killTweensOf(image)
    gsap.fromTo(
      image,
      { autoAlpha: 0.35, scale: 0.985 },
      {
        autoAlpha: 1,
        scale: 1,
        duration: 0.34,
        ease: 'power2.out',
        clearProps: 'transform,opacity,visibility'
      }
    )
  }

  if (badge) {
    gsap.killTweensOf(badge)
    gsap.fromTo(
      badge,
      { y: -8, autoAlpha: 0.7 },
      {
        y: 0,
        autoAlpha: 1,
        duration: 0.28,
        ease: 'power2.out',
        clearProps: 'transform,opacity,visibility'
      }
    )
  }
}

const animatePreviewState = async () => {
  await nextTick()
  const previewState = getScopedElement('.loading-state, .result-state, .empty-state')
  if (!previewState) return
  if (shouldReduceMotion()) {
    gsap.set(previewState, { clearProps: 'transform,opacity,visibility' })
    return
  }

  gsap.killTweensOf(previewState)
  const tl = gsap.timeline({ defaults: { ease: 'power3.out' } })
  tl.fromTo(
    previewState,
    { autoAlpha: 0, y: 22 },
    {
      autoAlpha: 1,
      y: 0,
      duration: 0.5,
      clearProps: 'transform,opacity,visibility'
    }
  )

  if (previewState.classList.contains('loading-state')) {
    const progressRing = previewState.querySelector('.n-progress') as HTMLElement | null
    const loadingInfo = previewState.querySelector('.loading-info') as HTMLElement | null
    if (progressRing) {
      tl.fromTo(
        progressRing,
        { scale: 0.9, autoAlpha: 0.2 },
        {
          scale: 1,
          autoAlpha: 1,
          duration: 0.55,
          ease: 'power4.out',
          clearProps: 'transform,opacity,visibility'
        },
        0.04
      )
    }
    if (loadingInfo) {
      tl.fromTo(
        loadingInfo,
        { y: 16, autoAlpha: 0 },
        {
          y: 0,
          autoAlpha: 1,
          duration: 0.36,
          clearProps: 'transform,opacity,visibility'
        },
        0.12
      )
    }
    return
  }

  if (previewState.classList.contains('result-state')) {
    const resultImage = previewState.querySelector('.result-img') as HTMLElement | null
    const actionBar = previewState.querySelector('.action-float-bar') as HTMLElement | null
    const paramsCard = previewState.querySelector('.params-details-card') as HTMLElement | null
    const thumbs = Array.from(previewState.querySelectorAll<HTMLElement>('.batch-thumb-item'))

    if (resultImage) {
      tl.fromTo(
        resultImage,
        { scale: 0.94, autoAlpha: 0, filter: 'blur(10px)' },
        {
          scale: 1,
          autoAlpha: 1,
          filter: 'blur(0px)',
          duration: 0.72,
          ease: 'power4.out',
          clearProps: 'transform,opacity,visibility,filter'
        },
        0.02
      )
    }

    if (actionBar) {
      tl.fromTo(
        actionBar,
        { y: 18, autoAlpha: 0 },
        {
          y: 0,
          autoAlpha: 1,
          duration: 0.38,
          clearProps: 'transform,opacity,visibility'
        },
        0.18
      )
    }

    if (thumbs.length > 0) {
      tl.fromTo(
        thumbs,
        { y: 10, autoAlpha: 0 },
        {
          y: 0,
          autoAlpha: 1,
          duration: 0.26,
          stagger: 0.04,
          clearProps: 'transform,opacity,visibility'
        },
        0.22
      )
    }

    if (paramsCard) {
      tl.fromTo(
        paramsCard,
        { y: 20, autoAlpha: 0 },
        {
          y: 0,
          autoAlpha: 1,
          duration: 0.4,
          clearProps: 'transform,opacity,visibility'
        },
        0.24
      )
    }
    return
  }

  const emptyIcon = previewState.querySelector('.empty-icon') as HTMLElement | null
  const emptyTitle = previewState.querySelector('h3') as HTMLElement | null
  const emptyText = previewState.querySelector('p') as HTMLElement | null

  if (emptyIcon) {
    tl.fromTo(
      emptyIcon,
      { y: 18, autoAlpha: 0, scale: 0.92 },
      {
        y: 0,
        autoAlpha: 1,
        scale: 1,
        duration: 0.56,
        ease: 'back.out(1.5)',
        clearProps: 'transform,opacity,visibility'
      },
      0.04
    )
  }
  if (emptyTitle) {
    tl.fromTo(
      emptyTitle,
      { y: 14, autoAlpha: 0 },
      {
        y: 0,
        autoAlpha: 1,
        duration: 0.32,
        clearProps: 'transform,opacity,visibility'
      },
      0.14
    )
  }
  if (emptyText) {
    tl.fromTo(
      emptyText,
      { y: 10, autoAlpha: 0 },
      {
        y: 0,
        autoAlpha: 1,
        duration: 0.28,
        clearProps: 'transform,opacity,visibility'
      },
      0.2
    )
  }
}

const setupEntranceAnimations = async () => {
  await nextTick()
  if (!pageRoot.value) return

  entranceMatchMedia?.revert()
  entranceMatchMedia = gsap.matchMedia()
  entranceMatchMedia.add(
    {
      isReduced: '(prefers-reduced-motion: reduce)',
      isMobile: '(max-width: 768px)'
    },
    (context) => {
      const { isReduced, isMobile } = context.conditions as { isReduced: boolean; isMobile: boolean }
      const panels = getScopedElements('.control-card, .preview-card')
      const focusBlocks = getScopedElements('.mode-tabs, .prompt-input-box, .generate-btn')
      const ratioCards = getScopedElements('.ratio-card')
      const orbs = getScopedElements('.card-orb')

      if (isReduced) {
        gsap.set([...panels, ...focusBlocks, ...ratioCards, ...orbs], { clearProps: 'transform,opacity,visibility' })
        return
      }

      const tl = gsap.timeline({ defaults: { ease: 'power3.out' } })
      tl.from(
        panels,
        {
          x: (index) => (index === 0 ? -28 : 28),
          y: isMobile ? 18 : 0,
          autoAlpha: 0,
          duration: 0.72,
          stagger: 0.08,
          clearProps: 'transform,opacity,visibility'
        }
      )
        .from(
          focusBlocks,
          {
            y: 18,
            autoAlpha: 0,
            duration: 0.52,
            stagger: 0.08,
            clearProps: 'transform,opacity,visibility'
          },
          0.16
        )
        .from(
          ratioCards,
          {
            y: 14,
            autoAlpha: 0,
            duration: 0.34,
            stagger: { each: 0.025, from: 'start' },
            clearProps: 'transform,opacity,visibility'
          },
          0.28
        )

      const loopTweens: gsap.core.Tween[] = []
      const leftOrb = getScopedElement('.card-orb-left')
      const rightOrb = getScopedElement('.card-orb-right')
      if (leftOrb) {
        loopTweens.push(
          gsap.to(leftOrb, {
            x: 18,
            y: -12,
            duration: 6,
            repeat: -1,
            yoyo: true,
            ease: 'sine.inOut'
          })
        )
      }
      if (rightOrb) {
        loopTweens.push(
          gsap.to(rightOrb, {
            x: -20,
            y: 14,
            duration: 7,
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
    pageRoot.value
  )
}

const initPageMotion = async () => {
  await setupEntranceAnimations()
  await animatePreviewState()
  await animateHistoryRail(false)
}

// ---- 局部重绘 Canvas 绘制 ----
const initInpaintCanvas = () => {
  const canvas = inpaintCanvas.value
  const img = inpaintBgImg.value
  if (!canvas || !img) return
  const rect = img.getBoundingClientRect()
  canvas.width = rect.width
  canvas.height = rect.height
  canvas.style.width = rect.width + 'px'
  canvas.style.height = rect.height + 'px'
  const ctx = canvas.getContext('2d')
  if (ctx) {
    ctx.fillStyle = 'rgba(0, 0, 0, 0)'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
  }
}

const getCanvasPos = (e: MouseEvent) => {
  const canvas = inpaintCanvas.value
  if (!canvas) return { x: 0, y: 0 }
  const rect = canvas.getBoundingClientRect()
  return {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top
  }
}

const startInpaintDraw = (e: MouseEvent) => {
  inpaintDrawing.value = true
  doInpaintDraw(e)
}

const doInpaintDraw = (e: MouseEvent) => {
  if (!inpaintDrawing.value) return
  const canvas = inpaintCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const pos = getCanvasPos(e)
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.lineWidth = inpaintBrushSize.value
  if (inpaintMode.value === 'draw') {
    ctx.globalCompositeOperation = 'source-over'
    ctx.strokeStyle = 'rgba(255, 255, 255, 1)'
  } else {
    ctx.globalCompositeOperation = 'destination-out'
    ctx.strokeStyle = 'rgba(0, 0, 0, 1)'
  }
  ctx.lineTo(pos.x, pos.y)
  ctx.stroke()
  ctx.beginPath()
  ctx.arc(pos.x, pos.y, inpaintBrushSize.value / 2, 0, Math.PI * 2)
  ctx.fill()
  ctx.beginPath()
  ctx.moveTo(pos.x, pos.y)
}

const stopInpaintDraw = () => {
  inpaintDrawing.value = false
  const canvas = inpaintCanvas.value
  if (canvas) {
    const ctx = canvas.getContext('2d')
    if (ctx) ctx.beginPath()
  }
}

const resetInpaintMask = () => {
  const canvas = inpaintCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (ctx) {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
  }
  inpaintMaskAssetId.value = null
}

// 上传遮罩图
const uploadInpaintMask = async (): Promise<number | null> => {
  const canvas = inpaintCanvas.value
  if (!canvas) return null
  const ctx = canvas.getContext('2d')
  if (!ctx) return null
  
  // 检查是否有像素被绘制
  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
  const pixels = imageData.data
  let hasContent = false
  for (let i = 3; i < pixels.length; i += 4) {
    if (pixels[i] > 0) {
      hasContent = true
      break
    }
  }
  if (!hasContent) {
    message.error('请先在参考图上绘制要重绘的区域')
    return null
  }

  return new Promise((resolve) => {
    canvas.toBlob(async (blob) => {
      if (!blob) {
        resolve(null)
        return
      }
      try {
        const activeProjectId = projectStore.activeProjectId
        if (!activeProjectId) {
          resolve(null)
          return
        }
        const maskFileName = 'mask_' + Date.now() + '.png'
        const formData = new FormData()
        formData.append('projectId', String(activeProjectId))
        formData.append('file', blob, maskFileName)
        const response = await assetApi.uploadAsset(formData)
        const uploaded = assetStore.normalizeAsset(getResponseData(response, '参考图上传结果待确认'))
        const confirmed = await confirmUploadedImageAsset(uploaded, {
          projectId: activeProjectId,
          fileName: maskFileName
        })
        inpaintMaskAssetId.value = confirmed.id
        resolve(confirmed.id)
      } catch {
        resolve(null)
      }
    }, 'image/png')
  })
}

onMounted(async () => {
  await loadPageContext()

  if (route.query.providerId) {
    const providerId = Number(route.query.providerId)
    const provider = providerStore
      .getProvidersForProject(projectStore.activeProjectId)
      .find(item => item.id === providerId && allowedImageProviderTypes.value.includes(item.type))
    if (provider) {
      form.providerId = provider.id
      handleProviderChange(provider.id)
    }
  }

  if (route.query.sourceAssetId) {
    const id = Number(route.query.sourceAssetId)
    const asset = assetStore.assets.find(a => a.id === id)
    if (asset) {
      generateMode.value = 'img2img'
      addRefAsset(asset)
    }
  }
  
  // 处理外部跳转携带的 Prompt
  if (route.query.prompt) {
    form.prompt = route.query.prompt as string
  }
  if (route.query.negPrompt) {
    form.negativePrompt = route.query.negPrompt as string
  }
  if (route.query.model) {
    form.modelName = route.query.model as string
  }
  if (route.query.count) {
    const count = parseInt(route.query.count as string)
    if (Number.isFinite(count) && count > 0) {
      form.count = count
    }
  }
  if (route.query.resolution) {
    form.resolution = route.query.resolution as string
  }
  if (route.query.quality) {
    form.quality = route.query.quality as string
  }
  if (route.query.aspectRatio) {
    form.aspectRatio = route.query.aspectRatio as string
  }
  if (route.query.aspectWidth) {
    const aspectWidth = parseInt(route.query.aspectWidth as string)
    if (Number.isFinite(aspectWidth) && aspectWidth > 0) {
      form.ratioWidth = aspectWidth
    }
  }
  if (route.query.aspectHeight) {
    const aspectHeight = parseInt(route.query.aspectHeight as string)
    if (Number.isFinite(aspectHeight) && aspectHeight > 0) {
      form.ratioHeight = aspectHeight
    }
  }
  if (route.query.cfg) {
    form.cfg = parseFloat(route.query.cfg as string)
  }
  if (route.query.steps) {
    form.steps = parseInt(route.query.steps as string)
  }
  if (route.query.size) {
    const parts = (route.query.size as string).split('x')
    if (parts.length === 2) {
      const w = parseInt(parts[0])
      const h = parseInt(parts[1])
      if (w > 0 && h > 0) {
        form.resolution = 'custom'
        form.customWidth = w
        form.customHeight = h
      }
    }
  }

  // 刷新后恢复未完成任务的轮询
  restoreActiveTaskPolling()
  void initPageMotion()
})

// 监听项目空间变化
watch(() => projectStore.activeProjectId, () => {
  activeTaskId.value = null
  selectedRefAssets.value = []
  refImagePreviews.value = []
  finishGeneratingState()
  void loadPageContext()
})

// 当前选中任务详情
const activeTask = computed(() => {
  if (activeTaskId.value) {
    return taskStore.tasks.find(t => t.id === activeTaskId.value)
  }
  // 默认返回最新生成的任务
  const currentProjHistory = taskHistory.value
  if (currentProjHistory.length > 0) {
    return currentProjHistory[0]
  }
  return null
})

const activeTaskProgress = computed(() => {
  const progress = activeTask.value?.progress
  return typeof progress === 'number' ? Math.max(0, Math.min(100, progress)) : null
})

function tryParseTaskRequestJson(task?: { requestJson?: string } | null) {
  if (!task?.requestJson) {
    return null
  }
  try {
    const parsed = JSON.parse(task.requestJson)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return null
    }
    return parsed as Record<string, unknown>
  } catch {
    return null
  }
}

function toPositiveInteger(value: unknown) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return Math.round(parsed)
}

function normalizeActualImageMode(payload: Record<string, unknown> | null) {
  if (!payload) {
    return '待确认'
  }
  const maskAssetId = toPositiveInteger(payload.maskAssetId)
  if (maskAssetId) {
    return '局部重绘'
  }
  const referenceAssetIds = Array.isArray(payload.referenceAssetIds)
    ? payload.referenceAssetIds.map(item => toPositiveInteger(item)).filter((item): item is number => item !== null)
    : []
  return referenceAssetIds.length > 0 ? '图生图' : '文生图'
}

const activeImageTaskRequest = computed(() => tryParseTaskRequestJson(activeTask.value))

function getImageTaskDisplayPrompt(task?: { prompt?: string; requestJson?: string } | null) {
  return normalizeTaskField(tryParseTaskRequestJson(task)?.prompt) || task?.prompt || ''
}

const actualImagePrompt = computed(() => {
  return normalizeTaskField(activeImageTaskRequest.value?.prompt) || currentAsset.value?.prompt || ''
})

const actualImageNegativePrompt = computed(() => {
  return normalizeTaskField(activeImageTaskRequest.value?.negativePrompt) || ''
})

const actualImageModelName = computed(() => {
  return normalizeTaskField(activeImageTaskRequest.value?.modelName) || normalizeTaskField(activeTask.value?.modelName) || currentAsset.value?.modelName || ''
})

const actualImageSizeLabel = computed(() => {
  const payload = activeImageTaskRequest.value
  if (!payload) {
    return '待确认'
  }
  const options = payload.options
  if (options && typeof options === 'object' && !Array.isArray(options)) {
    const width = toPositiveInteger((options as Record<string, unknown>).width)
    const height = toPositiveInteger((options as Record<string, unknown>).height)
    if (width && height) {
      return `${width} x ${height}px`
    }
  }
  const size = normalizeTaskField(payload.size)
  return size ? `${size.replace('x', ' x ')}px` : '待确认'
})

const actualImageCfgLabel = computed(() => {
  const options = activeImageTaskRequest.value?.options
  if (!options || typeof options !== 'object' || Array.isArray(options)) {
    return ''
  }
  const value = Number((options as Record<string, unknown>).cfg)
  return Number.isFinite(value) ? `${value}` : ''
})

const actualImageStepsLabel = computed(() => {
  const options = activeImageTaskRequest.value?.options
  if (!options || typeof options !== 'object' || Array.isArray(options)) {
    return ''
  }
  const value = toPositiveInteger((options as Record<string, unknown>).steps)
  return value ? `${value}` : ''
})

const actualImageQualityLabel = computed(() => {
  const options = activeImageTaskRequest.value?.options
  if (!options || typeof options !== 'object' || Array.isArray(options)) {
    return '待确认'
  }
  return normalizeTaskField((options as Record<string, unknown>).quality) || '待确认'
})

const actualImageModeLabel = computed(() => normalizeActualImageMode(activeImageTaskRequest.value))

const actualImageBatchCountLabel = computed(() => {
  const count = toPositiveInteger(activeImageTaskRequest.value?.count)
  return count ? `${count}` : ''
})

const formatElapsed = (seconds: number) => {
  const minutes = Math.floor(seconds / 60)
  const remain = seconds % 60
  return `已耗时 ${String(minutes).padStart(2, '0')}:${String(remain).padStart(2, '0')}`
}

const stopTaskPolling = () => {
  if (taskPollingTimer) {
    clearInterval(taskPollingTimer)
    taskPollingTimer = null
  }
}

const startElapsedTimer = () => {
  stopElapsedTimer()
  elapsedTimer = window.setInterval(() => {
    elapsedTick.value++
  }, 1000)
}

const stopElapsedTimer = () => {
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

function normalizeTaskField(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

async function loadLinkedTaskForAsset(asset: Asset) {
  const linkedTask = activeTask.value?.id === asset.taskId
    ? activeTask.value
    : (asset.taskId ? taskStore.tasks.find(task => task.id === asset.taskId) || null : null)
  if (linkedTask?.requestJson || !asset.taskId) {
    return linkedTask
  }
  try {
    const response = await taskApi.getTask(asset.taskId)
    const confirmedTask = taskStore.upsertTask(getResponseData(response, '图片任务结果待确认'))
    if (confirmedTask.projectId !== asset.projectId || confirmedTask.id !== asset.taskId) {
      throw new Error('图片任务结果待确认')
    }
    return confirmedTask
  } catch {
    if (linkedTask?.projectId === asset.projectId) {
      return linkedTask
    }
    throw new Error('图片任务结果待确认')
  }
}

async function resolveAssetGenerationFields(asset: Asset) {
  const linkedTask = await loadLinkedTaskForAsset(asset)
  let prompt = linkedTask?.prompt || asset.prompt || ''
  let negativePrompt = linkedTask?.negativePrompt || ''
  let modelName = linkedTask?.modelName || asset.modelName || ''
  let providerId = linkedTask?.providerId ? String(linkedTask.providerId) : ''
  let sourceLevel: 'request' | 'task' | 'asset' = linkedTask ? 'task' : 'asset'
  const requestPayload = tryParseTaskRequestJson(linkedTask)
  if (requestPayload) {
    sourceLevel = 'request'
    prompt = normalizeTaskField(requestPayload.prompt) || prompt
    negativePrompt = normalizeTaskField(requestPayload.negativePrompt)
    modelName = normalizeTaskField(requestPayload.modelName) || modelName
    const actualProviderId = toPositiveInteger(requestPayload.providerId)
    providerId = actualProviderId ? String(actualProviderId) : ''
  }
  return { prompt, negativePrompt, modelName, providerId, sourceLevel }
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

function assertImageGenerationTaskMatches(
  task: ReturnType<typeof taskStore.normalizeTask>,
  expected: {
    projectId: number
    providerId: number
    prompt: string
    negativePrompt: string
    modelName: string
    size: string
    count: number
    referenceAssetIds: number[]
    options: {
      modelName: string
      aspectRatio: string
      aspectWidth: number
      aspectHeight: number
      resolution: string
      quality: string
      width: number
      height: number
      cfg: number
      steps: number
    }
  }
) {
  const errorMessage = '图片任务提交结果待确认'
  if (task.projectId !== expected.projectId || task.providerId !== expected.providerId || task.taskType !== 'image') {
    throw new Error(errorMessage)
  }
  if (normalizeTaskField(task.prompt) !== expected.prompt) {
    throw new Error(errorMessage)
  }
  if (normalizeTaskField(task.negativePrompt) !== expected.negativePrompt) {
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
    || normalizeTaskField(requestPayload.negativePrompt) !== expected.negativePrompt
    || normalizeTaskField(requestPayload.modelName) !== expected.modelName
    || normalizeTaskField(requestPayload.size) !== expected.size
    || Number(requestPayload.count) !== expected.count
  ) {
    throw new Error(errorMessage)
  }
  const referenceAssetIds = Array.isArray(requestPayload.referenceAssetIds)
    ? requestPayload.referenceAssetIds.map(item => Number(item)).filter(item => Number.isFinite(item))
    : []
  if (
    referenceAssetIds.length !== expected.referenceAssetIds.length
    || referenceAssetIds.some((item, index) => item !== expected.referenceAssetIds[index])
  ) {
    throw new Error(errorMessage)
  }
  const options = requestPayload.options
  if (!options || typeof options !== 'object' || Array.isArray(options)) {
    throw new Error(errorMessage)
  }
  if (
    normalizeTaskField((options as Record<string, unknown>).modelName) !== expected.options.modelName
    || normalizeTaskField((options as Record<string, unknown>).aspectRatio) !== expected.options.aspectRatio
    || Number((options as Record<string, unknown>).aspectWidth) !== expected.options.aspectWidth
    || Number((options as Record<string, unknown>).aspectHeight) !== expected.options.aspectHeight
    || normalizeTaskField((options as Record<string, unknown>).resolution) !== expected.options.resolution
    || normalizeTaskField((options as Record<string, unknown>).quality) !== expected.options.quality
    || Number((options as Record<string, unknown>).width) !== expected.options.width
    || Number((options as Record<string, unknown>).height) !== expected.options.height
    || Number((options as Record<string, unknown>).cfg) !== expected.options.cfg
    || Number((options as Record<string, unknown>).steps) !== expected.options.steps
  ) {
    throw new Error(errorMessage)
  }
}

async function confirmSubmittedImageTask(
  task: ReturnType<typeof taskStore.normalizeTask>,
  expected: {
    projectId: number
    providerId: number
    prompt: string
    negativePrompt: string
    modelName: string
    size: string
    count: number
    referenceAssetIds: number[]
    options: {
      modelName: string
      aspectRatio: string
      aspectWidth: number
      aspectHeight: number
      resolution: string
      quality: string
      width: number
      height: number
      cfg: number
      steps: number
    }
  }
) {
  assertImageGenerationTaskMatches(task, expected)
  await taskStore.refresh({ projectId: expected.projectId })
  const confirmedFromList = taskStore.getTasksByProject(expected.projectId).find(item => item.id === task.id)
  const confirmed = confirmedFromList ?? taskStore.normalizeTask(getResponseData(await taskApi.getTask(task.id), '图片任务提交结果待确认'))
  assertImageGenerationTaskMatches(confirmed, expected)
  return confirmed
}

const finishGeneratingState = () => {
  generating.value = false
  taskCompleted.value = true
  stopTaskPolling()
  stopElapsedTimer()
}

function assertGeneratedImageAsset(
  asset: Asset,
  task: { id: number; projectId: number; prompt?: string; modelName?: string; resultAssetId?: number }
) {
  if (asset.projectId !== task.projectId) {
    throw new Error('图片结果待确认')
  }
  if (asset.taskId !== task.id) {
    throw new Error('图片结果待确认')
  }
  if (asset.assetType !== 'image') {
    throw new Error('图片结果待确认')
  }
  if (!asset.fileUrl && !asset.thumbUrl) {
    throw new Error('图片结果待确认')
  }
  if (task.resultAssetId && asset.id === task.resultAssetId && asset.taskId !== task.id) {
    throw new Error('图片结果待确认')
  }
}

function requireImageTaskAssetContext(task: { id: number; resultAssetId?: number }) {
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
    throw new Error('图片结果待确认')
  }
  return {
    id: candidate.id,
    projectId: Number(candidate.projectId),
    prompt: candidate.prompt,
    modelName: candidate.modelName,
    resultAssetId: candidate.resultAssetId
  }
}

function getConfirmedImageTaskAssets(task: { id: number; projectId: number; prompt?: string; modelName?: string; resultAssetId?: number }) {
  const assets = assetStore.assets.filter(asset => asset.taskId === task.id)
  assets.forEach(asset => assertGeneratedImageAsset(asset, task))
  if (task.resultAssetId) {
    const resultAsset = assetStore.assets.find(asset => asset.id === task.resultAssetId && asset.taskId === task.id)
    if (!resultAsset) {
      throw new Error('图片结果待确认')
    }
    assertGeneratedImageAsset(resultAsset, task)
    if (!assets.some(asset => asset.id === resultAsset.id)) {
      throw new Error('图片结果待确认')
    }
  }
  return assets
}

const syncTaskStatus = async (taskId: number) => {
  const detail = await taskApi.getTask(taskId)
  const task = taskStore.upsertTask(getResponseData(detail, '图片任务状态待确认'))
  activeTaskId.value = task.id
  if (task.status === 'success') {
    // 先精准加载本任务的结果资产(按 taskId,数据量小、快),保证图能立刻显示;
    // 全量资产刷新放后台进行,不阻塞结果展示(4k 大图时全量刷新较慢)。
    await ensureTaskAssetsLoaded(task)
    selectedBatchIndex.value = 0
    resultVersion.value++
    taskCompleted.value = true
    finishGeneratingState()
    message.success('图片生成完成')
    assetStore.refresh({ projectId: task.projectId }).catch(() => {})
    return task
  }
  if (task.status === 'failed') {
    taskCompleted.value = true
    finishGeneratingState()
    message.error(task.errorMessage || '图片生成失败')
    return task
  }
  return task
}

const startTaskPolling = (taskId: number) => {
  stopTaskPolling()
  taskPollingTimer = window.setInterval(async () => {
    // 页面隐藏时暂停；上一次同步未完成时跳过，避免请求重叠
    if (document.visibilityState !== 'visible' || taskSyncing) {
      return
    }
    taskSyncing = true
    try {
      await syncTaskStatus(taskId)
    } catch (err: unknown) {
      finishGeneratingState()
      message.error(getErrorMessage(err, '同步任务状态失败'))
    } finally {
      taskSyncing = false
    }
  }, 2000)
}

// 刷新页面后，若当前项目存在未完成(pending/running)任务，恢复其进度展示与轮询，
// 避免进度条永久卡住
const restoreActiveTaskPolling = () => {
  const pending = taskHistory.value.find(
    t => t.status === 'pending' || t.status === 'running'
  )
  if (!pending) return
  activeTaskId.value = pending.id
  selectedBatchIndex.value = 0
  taskCompleted.value = false
  generating.value = true
  submitting.value = false
  startElapsedTimer()
  startTaskPolling(pending.id)
}

// 关联获取该批生成的所有资产
let pendingFetchAsset: Promise<void> | null = null
const fetchedAsset = ref<number | null>(null)

// 确保某个任务的结果资产已加载进 store（轮询成功后调用）
const ensureTaskAssetsLoaded = async (task: { id: number; resultAssetId?: number }) => {
  const taskContext = requireImageTaskAssetContext(task)
  if (hasTaskAssetsLoaded(taskContext)) {
    getConfirmedImageTaskAssets(taskContext)
    return
  }
  if (!pendingFetchAsset) {
    pendingFetchAsset = (async () => {
      const response = await assetApi.getAssets({ taskId: task.id, projectId: taskContext.projectId })
      const assets = normalizeImageAssetList(getResponseData(response, '图片结果待确认'), '图片结果待确认')
      assets.forEach((item) => {
        upsertAsset(item)
      })
      const confirmedTask = requireImageTaskAssetContext(task)
      const confirmedAssets = getConfirmedImageTaskAssets(confirmedTask)
      if (!confirmedAssets.length || !hasTaskAssetsLoaded(confirmedTask)) {
        throw new Error('图片结果待确认')
      }
      fetchedAsset.value = Date.now()
    })().finally(() => {
      pendingFetchAsset = null
    })
  }
  await pendingFetchAsset
}

const hasTaskAssetsLoaded = (task: { id: number; resultAssetId?: number }) => {
  if (task.resultAssetId) {
    return assetStore.assets.some(asset => asset.id === task.resultAssetId && asset.taskId === task.id)
  }
  return assetStore.assets.some(asset => asset.taskId === task.id)
}

const upsertAsset = (asset: Asset) => {
  const index = assetStore.assets.findIndex(item => item.id === asset.id)
  if (index === -1) {
    assetStore.assets.unshift(asset)
  } else {
    assetStore.assets[index] = asset
  }
}

// 当前选中的单张资产 — 纯计算，无副作用
const currentAsset = computed(() => {
  void resultVersion.value
  void fetchedAsset.value
  const task = activeTask.value
  if (!task || task.status !== 'success') return null

  // 方式一：通过 resultAssetId 直接定位
  if (task.resultAssetId) {
    const found = assetStore.assets.find(a => a.id === task.resultAssetId && a.taskId === task.id)
    if (found) return found
  }

  // 方式二：按 taskId 筛选批量资产
  const batchAssets = assetStore.assets.filter(a => a.taskId === task.id)
  if (batchAssets.length > 0) {
    const idx = Math.min(selectedBatchIndex.value, batchAssets.length - 1)
    return batchAssets[idx]
  }
  return null
})

// 按 taskId 筛选的批量资产（用于缩略图网格）
const currentAssets = computed(() => {
  const task = activeTask.value
  if (!task || task.status !== 'success') return []
  return assetStore.assets.filter(a => a.taskId === task.id)
})

const getTaskPreviewAsset = (task: { id: number; resultAssetId?: number }) => {
  if (task.resultAssetId) {
    const exact = assetStore.assets.find(asset => asset.id === task.resultAssetId && asset.taskId === task.id)
    if (exact) {
      return exact
    }
  }
  return assetStore.assets.find(asset => asset.taskId === task.id) || null
}

// 批量张数（用于 UI 显示）
const batchTotal = computed(() => currentAssets.value.length)
const taskHistorySignature = computed(() => taskHistory.value.map(t => `${t.id}-${t.status}`).join('|'))
const previewVisualState = computed(() => {
  const status = activeTask.value?.status ?? 'idle'
  if (!taskCompleted.value && (generating.value || status === 'pending' || status === 'running')) {
    return `loading:${submitting.value ? 'submitting' : status}`
  }
  if (currentAsset.value) {
    return `result:${activeTask.value?.id ?? currentAsset.value.id}`
  }
  return 'empty'
})

watch(generateMode, () => {
  void animateModeSurface()
})

watch(() => form.aspectRatio, () => {
  void animateActiveSelection('.ratio-card.active')
})

watch(previewVisualState, () => {
  void animatePreviewState()
})

watch(() => selectedBatchIndex.value, (current, previous) => {
  if (current !== previous) {
    void animateResultImageSwap()
  }
})

watch(taskHistorySignature, (current, previous) => {
  if (current !== previous) {
    void animateHistoryRail(previous.length === 0)
  }
})

// 获取缩略图
const getTaskThumbUrl = (task: { id: number; resultAssetId?: number }) => {
  return getTaskPreviewAsset(task)?.thumbUrl || ''
}

const handleOptimizePrompt = async () => {
  const rawPrompt = form.prompt.trim()
  if (!rawPrompt) {
    message.error('请先输入一些提示词草稿！')
    return
  }
  if (!projectStore.activeProjectId) {
    message.error('请先进入一个项目空间后再润色提示词')
    return
  }
  optimizing.value = true
  try {
    const response = await generationApi.optimizeImagePrompt({
      projectId: projectStore.activeProjectId,
      providerId: form.providerId || undefined,
      prompt: rawPrompt
    })
    const payload = requirePromptOptimizeResult(getResponseData(response, '提示词润色接口未返回有效结果'))
    form.prompt = payload.prompt
    const providerName = payload.providerName || selectedProvider.value?.name
    message.success(providerName ? `提示词已通过 ${providerName} 润色` : '提示词已润色')
  } catch (error: unknown) {
    message.error(getErrorMessage(error, '提示词润色失败，请稍后重试'))
  } finally {
    optimizing.value = false
  }
}

// 从「Asset | string」中安全取出资产 id（字符串占位无 id 时返回 undefined）
function assetId(item: Asset | string | undefined): number | undefined {
  return item && typeof item !== 'string' ? item.id : undefined
}

function normalizeFileName(value: string) {
  return value.trim()
}

function assertUploadedImageAsset(asset: Asset, expected: { projectId: number; fileName?: string }) {
  if (asset.projectId !== expected.projectId) {
    throw new Error('参考图上传结果待确认')
  }
  if (expected.fileName && normalizeFileName(asset.fileName) !== normalizeFileName(expected.fileName)) {
    throw new Error('参考图上传结果待确认')
  }
  if (asset.assetType !== 'image' && asset.assetType !== 'reference') {
    throw new Error('参考图上传结果待确认')
  }
  if (!asset.thumbUrl && !asset.fileUrl) {
    throw new Error('参考图上传结果待确认')
  }
}

async function confirmUploadedImageAsset(
  uploaded: Asset,
  expected: { projectId: number; fileName?: string }
) {
  assertUploadedImageAsset(uploaded, expected)
  await assetStore.refresh({ projectId: expected.projectId, limit: 100 })
  const confirmed = assetStore
    .getAssetsByProject(expected.projectId)
    .find(asset => asset.id === uploaded.id)
  if (!confirmed) {
    throw new Error('参考图上传结果待确认')
  }
  assertUploadedImageAsset(confirmed, expected)
  return confirmed
}

// 添加参考图
function addRefAsset(assetOrUrl: Asset | string) {
  if (selectedRefAssets.value.filter(Boolean).length >= 16) {
    message.warning('最多 16 张参考图')
    return
  }
  selectedRefAssets.value.push(assetOrUrl)
  const url = typeof assetOrUrl === 'string' ? assetOrUrl : assetOrUrl.thumbUrl || assetOrUrl.fileUrl
  refImagePreviews.value.push(url)
}

function removeRef(index: number) {
  selectedRefAssets.value.splice(index, 1)
  refImagePreviews.value.splice(index, 1)
}

function removeRefPlaceholder(placeholderUrl: string) {
  const assetIndex = selectedRefAssets.value.findIndex(item => item === placeholderUrl)
  if (assetIndex >= 0) {
    selectedRefAssets.value.splice(assetIndex, 1)
  }
  const previewIndex = refImagePreviews.value.findIndex(item => item === placeholderUrl)
  if (previewIndex >= 0) {
    refImagePreviews.value.splice(previewIndex, 1)
  }
  URL.revokeObjectURL(placeholderUrl)
}

// 处理拖拽
function handleDrop(e: DragEvent) {
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    for (const file of Array.from(files)) {
      if (file.type.startsWith('image/') && selectedRefAssets.value.filter(Boolean).length < 16) {
        const url = URL.createObjectURL(file)
        addRefAsset(url)
        uploadRefFile(file, url)
      }
    }
  }
}

async function uploadRefFile(file: File, placeholderUrl: string) {
  try {
    const activeProjectId = projectStore.activeProjectId
    if (!activeProjectId) {
      throw new Error('请先选择一个项目空间')
    }
    const formData = new FormData()
    formData.append('projectId', String(activeProjectId))
    formData.append('file', file)
    const response = await assetApi.uploadAsset(formData)
    const asset = await confirmUploadedImageAsset(assetStore.normalizeAsset(getResponseData(response, '参考图上传结果待确认')), {
      projectId: activeProjectId,
      fileName: file.name
    })
    // 替换对应占位的 blob URL 为真实的 asset
    const idx = selectedRefAssets.value.findIndex(a => a === placeholderUrl)
    if (idx >= 0) {
      selectedRefAssets.value[idx] = asset
      const previewIdx = refImagePreviews.value.findIndex(item => item === placeholderUrl)
      if (previewIdx >= 0) {
        refImagePreviews.value[previewIdx] = asset.thumbUrl || asset.fileUrl
      }
      URL.revokeObjectURL(placeholderUrl)
    }
  } catch (err: unknown) {
    removeRefPlaceholder(placeholderUrl)
    message.error(getErrorMessage(err, '参考图上传失败'))
  }
}

// 触发图片上传
const handleTriggerUpload = () => {
  fileInput.value?.click()
}

const handleFileChange = async (e: Event) => {
  const target = e.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    for (const file of Array.from(target.files)) {
      if (file.type.startsWith('image/') && selectedRefAssets.value.filter(Boolean).length < 16) {
        const url = URL.createObjectURL(file)
        addRefAsset(url)
        await uploadRefFile(file, url)
      }
    }
    target.value = ''
  }
}

const handleStartGenerate = async () => {
  if (!form.providerId) {
    message.error('请配置并选择一个模型提供商！')
    return
  }
  if (!form.prompt) {
    message.error('请输入提示词指令描述您的艺术画面！')
    return
  }
  if (!form.resolution) {
    message.error('未加载到真实分辨率配置，请稍后重试')
    return
  }
  if (!form.quality) {
    message.error('未加载到真实质量配置，请稍后重试')
    return
  }

  const aspectRatio = getAspectRatioDimensions()
  const imageSize = getResolvedImageSize()
  const batchCount = sanitizeInteger(form.count, 1, 1, 8)

  form.count = batchCount
  if (form.aspectRatio === 'custom') {
    form.ratioWidth = aspectRatio.width
    form.ratioHeight = aspectRatio.height
  }
  if (form.resolution === 'custom') {
    form.customWidth = imageSize.width
    form.customHeight = imageSize.height
  }
  
  // 局部重绘模式：先上传遮罩图
  let maskId: number | undefined
  if (generateMode.value === 'inpaint') {
    const uploaded = await uploadInpaintMask()
    if (!uploaded) {
      generating.value = false
      return
    }
    if (selectedRefAssets.value.length === 0) {
      message.error('请先上传原始参考图')
      generating.value = false
      return
    }
    maskId = uploaded
  }

  stopTaskPolling()
  activeTaskId.value = null
  selectedBatchIndex.value = 0
  taskCompleted.value = false
  generating.value = true
  submitting.value = true
  startElapsedTimer()
  stopTaskPolling()
  try {
    const referenceAssetIds = generateMode.value === 'img2img'
      ? selectedRefAssets.value.map(assetId).filter((id): id is number => id != null)
      : []
    const commonParams = {
      projectId: projectStore.activeProjectId,
      providerId: form.providerId,
      prompt: form.prompt,
      modelName: form.modelName,
      negativePrompt: form.negativePrompt,
      size: `${imageSize.width}x${imageSize.height}`,
      count: batchCount,
      options: {
        modelName: form.modelName,
        aspectRatio: aspectRatio.label,
        aspectWidth: aspectRatio.width,
        aspectHeight: aspectRatio.height,
        resolution: form.resolution,
        quality: form.quality,
        width: imageSize.width,
        height: imageSize.height,
        cfg: form.cfg,
        steps: form.steps
      }
    }

    let res
    if (generateMode.value === 'inpaint') {
      res = await generationApi.generateInpaint({
        ...commonParams,
        referenceAssetIds: [assetId(selectedRefAssets.value[0])].filter((id): id is number => id != null),
        maskAssetId: maskId
      })
    } else {
      res = await generationApi.generateImage({
        ...commonParams,
        referenceAssetIds,
      })
    }

    const submittedTask = taskStore.normalizeTask(getResponseData(res, '图片任务提交结果待确认'))
    const expectedReferenceAssetIds = generateMode.value === 'inpaint'
      ? [assetId(selectedRefAssets.value[0])].filter((id): id is number => id != null)
      : referenceAssetIds
    const task = await confirmSubmittedImageTask(submittedTask, {
      projectId: commonParams.projectId,
      providerId: commonParams.providerId,
      prompt: commonParams.prompt.trim(),
      negativePrompt: normalizeTaskField(commonParams.negativePrompt),
      modelName: normalizeTaskField(commonParams.modelName),
      size: commonParams.size,
      count: commonParams.count,
      referenceAssetIds: expectedReferenceAssetIds,
      options: {
        modelName: normalizeTaskField(commonParams.options.modelName),
        aspectRatio: normalizeTaskField(commonParams.options.aspectRatio),
        aspectWidth: commonParams.options.aspectWidth,
        aspectHeight: commonParams.options.aspectHeight,
        resolution: normalizeTaskField(commonParams.options.resolution),
        quality: normalizeTaskField(commonParams.options.quality),
        width: commonParams.options.width,
        height: commonParams.options.height,
        cfg: commonParams.options.cfg,
        steps: commonParams.options.steps
      }
    })
    taskStore.upsertTask(task)
    activeTaskId.value = task.id
    selectedBatchIndex.value = 0

    if (task.status === 'success') {
      // 同步轮询:优先精准加载本任务资产,全量刷新放后台,避免 4k 大图时阻塞出图
      await ensureTaskAssetsLoaded(task)
      resultVersion.value++
      finishGeneratingState()
      message.success('图片生成完成')
      assetStore.refresh({ projectId: task.projectId }).catch(() => {})
    } else if (task.status === 'failed') {
      taskCompleted.value = true
      message.error(task.errorMessage || '图片生成失败')
    } else {
      // 任务已提交，轮询会持续跟踪
      startTaskPolling(task.id)
    }
  } catch (err: unknown) {
    taskCompleted.value = true
    message.error(getErrorMessage(err, '生成触发失败'))
  } finally {
    generating.value = false
    submitting.value = false
  }
}

function hasHistoryTask(taskId: number) {
  return taskHistory.value.some(task => task.id === taskId)
}

function ensureHistoryTasksRemoved(taskIds: number[]) {
  const remaining = taskIds.filter(id => hasHistoryTask(id))
  if (remaining.length > 0) {
    throw new Error('历史记录删除结果待确认')
  }
}

// 删除单条历史
const handleDeleteTask = async (taskId: number) => {
  try {
    await taskStore.deleteTask(taskId)
    if (activeTaskId.value === taskId) {
      activeTaskId.value = null
      selectedBatchIndex.value = 0
      taskCompleted.value = true
    }
    ensureHistoryTasksRemoved([taskId])
    message.success('已删除')
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '删除失败'))
  }
}

// 批量清空
const handleBatchClear = async () => {
  const ids = taskHistory.value
    .filter(t => t.status === 'success' || t.status === 'failed')
    .map(t => t.id)
  if (ids.length === 0) {
    message.info('当前没有可清空的历史记录')
    return
  }
  try {
    for (const id of ids) {
      await taskStore.deleteTask(id)
    }
    activeTaskId.value = null
    selectedBatchIndex.value = 0
    taskCompleted.value = true
    ensureHistoryTasksRemoved(ids)
    message.success('历史记录已清空')
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '清空失败'))
  }
}

// 历史记录选择切换
const handleSelectHistory = (task: ImageHistoryTask) => {
  activeTaskId.value = task.id
  selectedBatchIndex.value = 0
  if (task.status === 'success' || task.status === 'failed') {
    taskCompleted.value = true
  }
}

const handleInspectHistoryTask = async (task: ImageHistoryTask) => {
  if (task.status !== 'success') {
    message.error(task.errorMessage || '该任务生成失败，没有可查看的图片结果')
    return
  }
  try {
    await ensureTaskAssetsLoaded(task)
    handleSelectHistory(task)
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '该任务的图片结果待确认'))
  }
}

// 切换收藏
const handleToggleFavorite = async () => {
  if (currentAsset.value) {
    try {
      const updated = await assetStore.toggleFavorite(currentAsset.value.id)
      await assetStore.refresh({ projectId: currentAsset.value.projectId })
      const confirmed = assetStore.assets.find(asset => asset.id === updated.id)
      if (!confirmed || confirmed.favorite !== updated.favorite) {
        throw new Error('收藏状态待确认')
      }
      message.success(confirmed.favorite ? '资产收藏成功！' : '已取消收藏')
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '收藏状态更新失败'))
    }
  }
}

const handleDownload = () => {
  if (currentAsset.value) {
    message.info(`正在准备打包 ${currentAsset.value.fileName} 高清原图下载...`)
    const a = document.createElement('a')
    a.href = currentAsset.value.fileUrl
    a.download = currentAsset.value.fileName
    a.target = '_blank'
    a.click()
  }
}

// 图生视频一键带入跳转
const handleShareToCommunity = async () => {
  if (!currentAsset.value) { message.error('请先生成图片'); return }
  const asset = currentAsset.value
  let sharePrompt = asset.prompt || ''
  let shareNegativePrompt = ''
  let shareModel = asset.modelName || ''
  let sourceLevel: 'request' | 'task' | 'asset' = 'asset'
  try {
    const resolvedFields = await resolveAssetGenerationFields(asset)
    sharePrompt = resolvedFields.prompt
    shareNegativePrompt = resolvedFields.negativePrompt
    shareModel = resolvedFields.modelName
    sourceLevel = resolvedFields.sourceLevel
  } catch {
    // 社区分享允许在缺少任务详情时退回到资产表已记录字段
  }
  router.push({
    path: '/community',
    query: {
      sharePrompt,
      shareNegativePrompt,
      shareModel,
      shareImage: asset.fileUrl || ''
    }
  })
  message.success(
    sourceLevel === 'request'
      ? '已跳转至社区分享页，并带入真实提示词与模型'
      : sourceLevel === 'task'
        ? '已跳转至社区分享页，并带入任务已记录的提示词与模型'
        : '已跳转至社区分享页，并带入资产当前已记录的提示词与模型'
  )
}

const handleToVideo = async () => {
  if (!currentAsset.value) {
    return
  }
  const asset = currentAsset.value
  const query: Record<string, string> = {
    sourceAssetId: asset.id.toString()
  }
  let sourceLevel: 'request' | 'task' | 'asset' = 'asset'
  try {
    const resolvedFields = await resolveAssetGenerationFields(asset)
    sourceLevel = resolvedFields.sourceLevel
    if (resolvedFields.prompt) {
      query.prompt = resolvedFields.prompt
    }
    if (resolvedFields.modelName) {
      query.model = resolvedFields.modelName
    }
    if (resolvedFields.providerId) {
      query.providerId = resolvedFields.providerId
    }
  } catch {
    if (asset.prompt) {
      query.prompt = asset.prompt
    }
    if (asset.modelName) {
      query.model = asset.modelName
    }
  }
  router.push({
    path: '/generate/video',
    query
  })
  message.success(
    sourceLevel === 'request'
      ? '已将当前图片设为视频首帧，并优先带入真实提示词、模型与可确认的提供商参数'
      : sourceLevel === 'task'
        ? '已将当前图片设为视频首帧，并优先带入任务已记录的提示词、模型与提供商参数'
        : '已将当前图片设为视频首帧，并带入资产当前已记录的提示词与模型'
  )
}

onBeforeUnmount(() => {
  entranceMatchMedia?.revert()
  finishGeneratingState()
})
</script>

<style scoped>
.generate-container {
  position: relative;
  height: calc(100vh - 120px);
  padding-bottom: 20px;
  color: var(--text-primary);
  overflow: hidden;
  isolation: isolate;
}

.generate-container::before,
.generate-container::after {
  content: '';
  position: absolute;
  border-radius: 999px;
  pointer-events: none;
  filter: blur(28px);
  z-index: 0;
}

.generate-container::before {
  width: 320px;
  height: 320px;
  top: -90px;
  left: -80px;
  background: radial-gradient(circle, rgba(16, 185, 129, 0.18) 0%, rgba(16, 185, 129, 0.04) 48%, transparent 72%);
}

.generate-container::after {
  width: 380px;
  height: 380px;
  right: -120px;
  bottom: -140px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.16) 0%, rgba(59, 130, 246, 0.04) 50%, transparent 76%);
}

.generate-container > * {
  position: relative;
  z-index: 1;
}

.glass-card {
  position: relative;
  overflow: hidden;
  isolation: isolate;
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.86) 0%, rgba(7, 10, 18, 0.78) 100%),
    rgba(15, 23, 42, 0.4) !important;
  backdrop-filter: blur(16px);
  border: 1px solid var(--border-color) !important;
  border-radius: 16px !important;
  box-shadow: 0 20px 48px rgba(0, 0, 0, 0.22);
}

.control-card :deep(.n-card__content),
.preview-card :deep(.n-card__content) {
  position: relative;
  z-index: 1;
}

.card-orb {
  position: absolute;
  width: 220px;
  height: 220px;
  border-radius: 999px;
  pointer-events: none;
  opacity: 0.7;
  filter: blur(24px);
  z-index: 0;
}

.card-orb-left {
  top: -110px;
  right: -80px;
  background: radial-gradient(circle, rgba(16, 185, 129, 0.34) 0%, rgba(59, 130, 246, 0.12) 42%, transparent 72%);
}

.card-orb-right {
  top: -100px;
  left: -70px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.3) 0%, rgba(16, 185, 129, 0.12) 44%, transparent 72%);
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

.prompt-input-box {
  position: relative;
  width: 100%;
}

.optimize-btn {
  position: absolute;
  right: 8px;
  bottom: 8px;
  background: rgba(139, 92, 246, 0.15) !important;
  border: 1px solid rgba(139, 92, 246, 0.3) !important;
}

.optimize-icon {
  width: 12px;
  height: 12px;
  margin-right: 4px;
}

/* 拖拽参考图区 */
.ref-image-section {
  display: flex;
  flex-direction: column;
  margin-bottom: 20px;
}

.ref-label {
  font-size: 13px;
  color: #d1d5db;
  font-weight: 500;
  margin-bottom: 8px;
}

.upload-drag-area {
  height: 110px;
  border: 2px dashed rgba(255, 255, 255, 0.15);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.01);
  transition: all 0.3s;
  overflow: hidden;
}

.upload-drag-area:hover {
  border-color: #10b981;
  background: rgba(16, 185, 129, 0.03);
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 12px;
  color: #9ca3af;
}

.upload-cloud-icon {
  width: 28px;
  height: 28px;
  color: #9ca3af;
  margin-bottom: 8px;
}

.upload-tip {
  font-size: 10px;
  color: #6b7280;
  margin-top: 4px;
}

.preview-container {
  position: relative;
  width: 100%;
  height: 100%;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.change-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
  font-size: 12px;
}

.preview-container:hover .change-overlay {
  opacity: 1;
}

.hidden-input {
  display: none;
}

/* 比例控制卡 */
.aspect-ratio-group {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  width: 100%;
}

.ratio-card {
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.02);
  border-radius: 12px;
  padding: 10px 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  min-height: 78px;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.ratio-card:hover {
  border-color: rgba(16, 185, 129, 0.3);
  background: rgba(16, 185, 129, 0.02);
  box-shadow: 0 4px 15px rgba(16, 185, 129, 0.05);
}

.ratio-card.active {
  border-color: #10b981;
  background: rgba(16, 185, 129, 0.08);
  box-shadow: 0 0 15px rgba(16, 185, 129, 0.15);
}

/* 统一示意图标对齐基线 */
.shape-container {
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 6px;
  width: 100%;
}

.ratio-shape {
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 3px;
  transition: all 0.3s;
}

.ratio-card.active .ratio-shape {
  border-color: #10b981;
  background: rgba(16, 185, 129, 0.4);
}

.ratio-shape.ratio-1-1 { width: 20px; height: 20px; }
.ratio-shape.ratio-4-3 { width: 24px; height: 18px; }
.ratio-shape.ratio-3-4 { width: 18px; height: 24px; }
.ratio-shape.ratio-3-2 { width: 26px; height: 17px; }
.ratio-shape.ratio-2-3 { width: 17px; height: 26px; }
.ratio-shape.ratio-16-9 { width: 28px; height: 16px; }
.ratio-shape.ratio-9-16 { width: 16px; height: 28px; }
.ratio-shape.ratio-21-9 { width: 30px; height: 13px; }
.ratio-shape.ratio-9-21 { width: 13px; height: 30px; }
.ratio-shape.ratio-custom { width: 20px; height: 20px; border-style: dashed; border-radius: 50%; }

.ratio-label {
  font-size: 11px;
  color: #9ca3af;
  white-space: nowrap;
  transition: color 0.2s;
}

.ratio-card.active .ratio-label {
  color: #10b981;
}

/* 豪华跨列自定义卡片 */
.custom-ratio-card {
  grid-column: span 3;
  flex-direction: row !important;
  gap: 12px;
  min-height: 48px !important;
  padding: 6px 16px !important;
}

.custom-ratio-card .shape-container {
  margin-bottom: 0 !important;
  width: auto !important;
}

.slider-val {
  text-align: right;
  font-size: 12px;
  color: #10b981;
  font-weight: 600;
  margin-top: -8px;
}

.btn-area {
  padding-top: 15px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.generate-btn {
  background: linear-gradient(135deg, #10b981, #3b82f6) !important;
  border: none !important;
  box-shadow: 0 4px 15px rgba(16, 185, 129, 0.3) !important;
}

.gen-zap {
  width: 16px;
  height: 16px;
  margin-right: 6px;
}

/* 右侧预览卡样式 */
.preview-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

/* 占位空状态 */
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
  background: radial-gradient(circle, rgba(16, 185, 129, 0.06) 0%, transparent 70%);
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

/* 加载中高拟真进度环 */
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
  background: radial-gradient(circle, rgba(16, 185, 129, 0.12) 0%, transparent 70%);
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
  color: #fff;
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
  color: #10b981;
  font-weight: 500;
}

/* 生成成功结果展示 */
.result-state {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding-bottom: 120px; /* 留出历史栏的高度 */
}

.image-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 100%;
  max-height: 480px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4);
}

.result-img {
  max-width: 100%;
  max-height: 480px;
  width: auto;
  height: auto;
  object-fit: contain;
  cursor: pointer;
  transition: transform 0.3s;
  background-color: #05070c;
}

.result-img:hover {
  transform: scale(1.005);
}

.action-float-bar {
  margin-top: 16px;
  max-width: 100%;
  flex-wrap: wrap;
  justify-content: center;
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.78), rgba(30, 41, 59, 0.6));
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

.float-action-btn {
  box-shadow: 0 4px 10px rgba(245, 158, 11, 0.2);
}

.favorited {
  color: #ef4444 !important;
  fill: #ef4444 !important;
}

.params-details-card {
  width: 100%;
  margin-top: 16px;
  padding: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02));
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.params-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
}

.model-badge {
  color: #10b981;
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

.prompt-display-negative {
  color: #fca5a5;
}

/* 历史生成侧栏 */
.history-section {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  padding: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.56), rgba(15, 23, 42, 0.72));
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
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.56), rgba(15, 23, 42, 0.72));
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
  border-color: #10b981;
  box-shadow: 0 0 10px rgba(16, 185, 129, 0.3);
}

.history-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.custom-config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
  margin-top: 12px;
}

/* 自定义参数输入组件 */
.custom-config-inputs {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  width: 100%;
  margin-top: 12px;
}

.custom-input-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.custom-input-label {
  font-size: 11px;
  color: #9ca3af;
  font-weight: 500;
  letter-spacing: 0.3px;
}

.custom-input-sep {
  color: #4b5563;
  font-size: 16px;
  font-weight: bold;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.config-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #94a3b8;
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

.lightbox-content {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.lightbox-img {
  max-width: 100%;
  max-height: 80vh;
  object-fit: contain;
  border-radius: 8px;
}

/* ---- 批量结果网格 ---- */
.batch-indicator {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  padding: 4px 12px;
  border-radius: 20px;
  z-index: 5;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.batch-count {
  font-size: 13px;
  font-weight: 600;
  color: #f3f4f6;
  min-width: 50px;
  text-align: center;
}

.batch-thumbnails {
  display: flex;
  gap: 8px;
  padding: 10px 0;
  overflow-x: auto;
}

.batch-thumb-item {
  position: relative;
  width: 64px;
  min-width: 64px;
  height: 64px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.3s, transform 0.2s;
  opacity: 0.6;
}

.batch-thumb-item:hover {
  opacity: 0.9;
  transform: scale(1.05);
}

.batch-thumb-item.active {
  border-color: #10b981;
  opacity: 1;
  box-shadow: 0 0 12px rgba(16, 185, 129, 0.3);
}

.batch-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.batch-thumb-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  background: rgba(0, 0, 0, 0.5);
  text-align: center;
  padding: 1px 0;
}

.batch-thumb-idx {
  font-size: 10px;
  color: #fff;
  font-weight: 600;
}

.batch-badge {
  font-size: 11px;
  color: #10b981;
  background: rgba(16, 185, 129, 0.1);
  padding: 2px 8px;
  border-radius: 10px;
}

/* ---- 局部重绘 Canvas 样式 ---- */
.inpaint-canvas-wrapper {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.inpaint-canvas-container {
  position: relative;
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.inpaint-bg-img {
  display: block;
  width: 100%;
  height: auto;
}

.inpaint-overlay-canvas {
  position: absolute;
  top: 0;
  left: 0;
  cursor: crosshair;
  z-index: 2;
}

.inpaint-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: rgba(128,128,128,0.03);
  border-radius: 10px;
  border: 1px solid var(--border-color);
}

.toolbar-label {
  font-size: 12px;
  color: #9ca3af;
}

.brush-size-val {
  font-size: 12px;
  color: #10b981;
  font-weight: 600;
  min-width: 40px;
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

.history-tabs {
  max-width: 300px;
}

/* 多参考图网格 */
.ref-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.ref-thumb-box { position: relative; width: 64px; height: 64px; border-radius: 8px; overflow: hidden; border: 1px solid var(--border-color); }
.ref-thumb-img { width: 100%; height: 100%; object-fit: cover; }
.ref-thumb-remove { position: absolute; top: 1px; right: 1px; width: 16px; height: 16px; border-radius: 50%; background: rgba(239,68,68,0.85); color: #fff; font-size: 10px; line-height: 16px; text-align: center; cursor: pointer; }
.ref-add-box { width: 64px; height: 64px; border: 2px dashed var(--border-color); border-radius: 8px; display: flex; flex-direction: column; align-items: center; justify-content: center; cursor: pointer; gap: 4px; transition: border-color .2s; }
.ref-add-box:hover { border-color: #10b981; }
.ref-add-box .upload-cloud-icon { width: 18px; height: 18px; opacity: 0.5; }
.ref-add-box span { font-size: 10px; color: var(--text-muted); }
.ref-hint { font-size: 10px; color: var(--text-muted); margin-top: 6px; }

/* 画廊模式 */
.gallery-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.gallery-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; max-height: 60vh; overflow-y: auto; padding: 4px; }
.gallery-card { border: 1px solid var(--border-color); border-radius: 10px; overflow: hidden; transition: all .2s; }
.gallery-card:hover { border-color: #10b981; transform: translateY(-2px); }
.gallery-card.gallery-active { border-color: #10b981; box-shadow: 0 0 12px rgba(16,185,129,0.2); }
.gallery-preview { height: 140px; overflow: hidden; cursor: pointer; background: rgba(128,128,128,0.03); }
.gallery-img { width: 100%; height: 100%; object-fit: cover; }
.gallery-loading, .gallery-failed { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; }
.gallery-loading-text { font-size: 11px; color: var(--text-muted); }
.gallery-failed span { font-size: 13px; opacity: 0.5; }
.gallery-meta { padding: 8px 10px; display: flex; flex-direction: column; gap: 6px; }
.gallery-prompt { font-size: 11px; color: var(--text-secondary); }
.gallery-bottom { display: flex; justify-content: space-between; align-items: center; }
.gallery-date { font-size: 10px; color: var(--text-muted); }

.param-compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.param-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px;
  background: rgba(128,128,128,0.03);
  border-radius: 6px;
}
.param-label {
  font-size: 10px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.param-value {
  font-size: 12px;
  color: var(--text-primary);
  word-break: break-all;
}

.thumb-failed-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(239,68,68,0.15);
}
.thumb-failed-text {
  font-size: 10px;
  color: #ef4444;
  font-weight: 600;
}

@media (max-width: 768px) {
  .card-orb {
    width: 160px;
    height: 160px;
    opacity: 0.55;
  }
}
</style>
