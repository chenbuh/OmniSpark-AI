<template>
  <div class="workflows-container">
    <div class="page-header">
      <div>
        <h2>工作流编排 (Workflows)</h2>
        <p class="subtitle">把图像生成、视频生成和字幕处理编成可复用链路，减少重复操作。</p>
      </div>
      <n-space>
        <n-button secondary :loading="loading" @click="loadPageData">
          <template #icon><RefreshCw /></template>
          刷新
        </n-button>
        <n-button type="primary" @click="openCreateEditor">
          <template #icon><Plus /></template>
          新建工作流
        </n-button>
      </n-space>
    </div>

    <n-row :gutter="24">
      <n-col :span="9">
        <n-card class="glass-card" :bordered="false">
          <template #header>
            <div class="card-header-row">
              <span>我的工作流 ({{ filteredWorkflows.length }})</span>
              <n-input
                v-model:value="searchQuery"
                clearable
                size="small"
                placeholder="搜索名称或描述"
                style="width: 180px;"
              >
                <template #prefix><Search class="inline-icon" /></template>
              </n-input>
            </div>
          </template>

          <div v-if="filteredWorkflows.length > 0" class="wf-list">
            <div
              v-for="wf in pagedWorkflows"
              :key="wf.id"
              class="wf-card"
              :class="{ 'active-wf': selectedWorkflow?.id === wf.id }"
              @click="selectWorkflow(wf)"
            >
              <div class="wf-icon-box">
                <GitBranch class="wf-icon" />
              </div>
              <div class="wf-info">
                <span class="wf-name">{{ wf.name }}</span>
                <span class="wf-meta">{{ wf.steps.length }} 个步骤 · {{ workflowTypeSummary(wf.steps) }}</span>
                <span class="wf-desc">{{ wf.description || '未填写描述' }}</span>
              </div>
              <n-button size="tiny" type="error" tertiary @click.stop="handleDelete(wf.id)">
                <template #icon><Trash2 /></template>
              </n-button>
            </div>
          </div>
          <n-empty v-else description="当前项目还没有工作流" />
          <div class="pager" v-if="filteredWorkflows.length > wfPageSize">
            <n-pagination v-model:page="wfPage" :page-size="wfPageSize" :item-count="filteredWorkflows.length" simple />
          </div>
        </n-card>
      </n-col>

      <n-col :span="15">
        <n-card v-if="selectedWorkflow" class="glass-card" :bordered="false">
          <template #header>
            <div class="card-header-row">
              <div class="detail-head">
                <span>{{ selectedWorkflow.name }}</span>
                <small>{{ selectedWorkflow.description || '暂无描述' }}</small>
              </div>
              <n-space>
                <n-button type="warning" :loading="executing" @click="handleExecute">
                  <template #icon><Play /></template>
                  执行工作流
                </n-button>
                <n-button secondary @click="cloneWorkflow(selectedWorkflow)">
                  <template #icon><Copy /></template>克隆
                </n-button>
                <n-button secondary @click="openEditEditor(selectedWorkflow)">编辑</n-button>
              </n-space>
            </div>
          </template>

          <div class="summary-strip">
            <div class="summary-chip">
              <span>步骤数</span>
              <strong>{{ selectedWorkflow.steps.length }}</strong>
            </div>
            <div class="summary-chip">
              <span>结构</span>
              <strong>{{ workflowTypeSummary(selectedWorkflow.steps) }}</strong>
            </div>
            <div class="summary-chip">
              <span>最近运行</span>
              <strong>{{ runs[0]?.status ? runStatusLabel(runs[0].status) : '暂无' }}</strong>
            </div>
          </div>

          <div class="steps-list">
            <div v-for="(step, idx) in selectedWorkflow.steps" :key="idx" class="step-card">
              <div class="step-index">{{ idx + 1 }}</div>
              <div class="step-body">
                <div class="step-head">
                  <n-tag size="small" :type="stepTypeColor(step.type)" round>{{ stepTypeLabel(step.type) }}</n-tag>
                  <span class="step-desc">{{ step.prompt || step.description || '未填写描述' }}</span>
                </div>
                <div class="step-meta">
                  <code v-if="step.providerId">Provider #{{ step.providerId }}</code>
                  <code v-if="step.modelName">{{ step.modelName }}</code>
                  <span v-if="step.size">{{ step.size }}</span>
                  <span v-if="step.duration">{{ step.duration }}</span>
                  <span v-if="step.count">x{{ step.count }}</span>
                  <span v-if="step.usePreviousAsReference">继承上一步素材</span>
                  <span v-if="step.voice">自动配音</span>
                </div>
              </div>
            </div>
          </div>

          <n-collapse style="margin-top: 18px;">
            <n-collapse-item name="runs" title="运行历史">
              <div v-if="runs.length > 0" class="runs-list">
                <div v-for="run in runs" :key="run.id" class="run-card">
                  <div class="run-head">
                    <div class="run-head-left">
                      <n-tag size="small" :type="runStatusType(run.status)">{{ runStatusLabel(run.status) }}</n-tag>
                      <span class="run-time">{{ formatRunTime(run) }}</span>
                    </div>
                    <span class="run-step">进行到步骤 {{ (run.currentStep ?? 0) + 1 }}</span>
                  </div>

                  <div v-if="run.errorMessage" class="run-error">
                    {{ run.errorMessage }}
                  </div>

                  <div v-if="parseRunResults(run).length > 0" class="run-results">
                    <div v-for="result in parseRunResults(run)" :key="`${run.id}-${result.stepIndex}`" class="run-result-item">
                      <div class="run-result-head">
                        <span>步骤 {{ (result.stepIndex ?? 0) + 1 }} · {{ stepTypeLabel(result.stepType || '') }}</span>
                        <n-tag size="tiny" :type="result.status === 'success' ? 'success' : 'error'">{{ result.status === 'success' ? '成功' : '失败' }}</n-tag>
                      </div>
                      <div class="run-result-body">
                        <span v-if="result.message">{{ result.message }}</span>
                        <span v-if="result.taskId">任务 #{{ result.taskId }}</span>
                        <span v-if="result.assetId">资产 #{{ result.assetId }}</span>
                        <span v-if="result.subtitleId">字幕 #{{ result.subtitleId }}</span>
                        <span v-if="result.voiceUrl">已生成配音</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <n-empty v-else description="暂无运行记录" style="padding: 12px 0;" />
            </n-collapse-item>
          </n-collapse>
        </n-card>

        <n-card v-else class="glass-card" :bordered="false">
          <n-empty description="从左侧选择一个工作流，或新建一条自动化创作链路" />
        </n-card>
      </n-col>
    </n-row>

    <n-modal v-model:show="showEditor" preset="card" :title="editorMode === 'create' ? '新建工作流' : '编辑工作流'" style="width: 920px;" closable>
      <n-form :model="editForm" label-placement="top">
        <n-row :gutter="16">
          <n-col :span="12">
            <n-form-item label="工作流名称">
              <n-input v-model:value="editForm.name" placeholder="例如：海报生图 → 宣传短片 → 中文字幕" />
            </n-form-item>
          </n-col>
          <n-col :span="12">
            <n-form-item label="描述">
              <n-input v-model:value="editForm.description" placeholder="说明这个流程适合什么场景" />
            </n-form-item>
          </n-col>
        </n-row>

        <div class="steps-template-hint">
          <span>从模板开始：</span>
          <n-button size="tiny" secondary @click="applyTemplate('image-only')">仅生图</n-button>
          <n-button size="tiny" secondary @click="applyTemplate('image-to-video')">生图 → 视频</n-button>
          <n-button size="tiny" secondary @click="applyTemplate('full-pipeline')">生图 → 视频 → 字幕/配音</n-button>
        </div>

        <div class="beginner-tip">
          直接按步骤填写即可，不需要编写任何配置代码。先写清楚每一步“要生成什么”，再补充模型和素材来源。
        </div>

        <div class="editor-steps">
          <div v-for="(step, idx) in stepDrafts" :key="idx" class="editor-step-card">
            <div class="editor-step-head">
              <div class="editor-step-title">
                <span class="step-no">步骤 {{ idx + 1 }}</span>
                <n-tag size="small" round :type="stepTypeColor(step.type)">{{ stepTypeLabel(step.type) }}</n-tag>
              </div>
              <n-space>
                <n-button size="tiny" secondary :disabled="idx === 0" @click="moveStep(idx, -1)">
                  <template #icon><ArrowUp /></template>
                </n-button>
                <n-button size="tiny" secondary :disabled="idx === stepDrafts.length - 1" @click="moveStep(idx, 1)">
                  <template #icon><ArrowDown /></template>
                </n-button>
                <n-button size="tiny" type="error" tertiary @click="removeStep(idx)">
                  <template #icon><Trash2 /></template>
                </n-button>
              </n-space>
            </div>

            <n-row :gutter="12">
              <n-col :span="8">
                <n-form-item label="步骤类型">
                  <n-select v-model:value="step.type" :options="stepTypeOptions" @update:value="() => normalizeStep(step)" />
                </n-form-item>
              </n-col>
              <n-col :span="16">
                <n-form-item label="这一步要做什么">
                  <n-input v-model:value="step.prompt" :placeholder="stepPromptPlaceholder(step.type)" />
                </n-form-item>
              </n-col>
            </n-row>

            <template v-if="step.type === 'image' || step.type === 'video'">
              <n-row :gutter="12">
                <n-col :span="8">
                  <n-form-item label="模型提供商">
                    <n-select v-model:value="step.providerId" :options="providerOptions(step.type)" placeholder="选择 Provider" />
                  </n-form-item>
                </n-col>
                <n-col :span="8">
                  <n-form-item label="模型名称">
                    <n-input v-model:value="step.modelName" placeholder="可留空，走提供商默认模型" />
                  </n-form-item>
                </n-col>
                <n-col :span="8" v-if="step.type === 'image'">
                  <n-form-item label="输出尺寸">
                    <n-select v-model:value="step.size" :options="imageSizeOptions" />
                  </n-form-item>
                </n-col>
                <n-col :span="8" v-else>
                  <n-form-item label="视频时长">
                    <n-select v-model:value="step.duration" :options="videoDurationOptions" />
                  </n-form-item>
                </n-col>
              </n-row>
            </template>

            <template v-if="step.type === 'image'">
              <n-row :gutter="12">
                <n-col :span="16">
                  <n-form-item label="负向提示词">
                    <n-input v-model:value="step.negativePrompt" placeholder="可选，排除画面中的不希望元素" />
                  </n-form-item>
                </n-col>
                <n-col :span="8">
                  <n-form-item label="生成张数">
                    <n-input-number v-model:value="step.count" :min="1" :max="8" style="width: 100%;" />
                  </n-form-item>
                </n-col>
              </n-row>
              <n-checkbox v-model:checked="step.usePreviousAsReference">
                使用上一步输出资产作为参考图
              </n-checkbox>
            </template>

            <template v-if="step.type === 'video'">
              <n-row :gutter="12">
                <n-col :span="12">
                  <n-form-item label="首帧来源">
                    <n-select
                      v-model:value="step.sourceAssetId"
                      clearable
                      :options="imageAssetOptions"
                      placeholder="留空则默认使用上一步图片资产"
                    />
                  </n-form-item>
                </n-col>
                <n-col :span="12">
                  <n-form-item label="尾帧来源">
                    <n-select
                      v-model:value="step.endAssetId"
                      clearable
                      :options="imageAssetOptions"
                      placeholder="可选，指定尾帧资产"
                    />
                  </n-form-item>
                </n-col>
              </n-row>
            </template>

            <template v-if="step.type === 'subtitle'">
              <n-row :gutter="12">
                <n-col :span="12">
                  <n-form-item label="字幕语言">
                    <n-select v-model:value="step.language" :options="subtitleLanguageOptions" />
                  </n-form-item>
                </n-col>
                <n-col :span="12">
                  <n-form-item label="配音">
                    <n-switch v-model:value="step.voice" />
                  </n-form-item>
                </n-col>
              </n-row>
              <div class="step-tip">默认会对上一步生成的视频执行真实字幕转写；开启配音后会继续自动生成一版语音文件。</div>
            </template>
          </div>
        </div>

        <div class="add-step-actions">
          <n-button
            v-for="option in stepTypeOptions"
            :key="option.value"
            dashed
            class="add-step-btn"
            @click="addStep(option.value as WorkflowStepType)"
          >
            <template #icon><Plus /></template>
            添加{{ option.label }}
          </n-button>
        </div>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button @click="showEditor = false">取消</n-button>
          <n-button type="primary" @click="handleSave">{{ editorMode === 'create' ? '创建工作流' : '保存修改' }}</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { useAssetStore } from '@/store/asset'
import { useModelProviderStore } from '@/store/provider'
import { useProjectStore } from '@/store/project'
import { generationApi, type GenerationMetaVO } from '@/api/generation'
import {
  workflowApi,
  type WorkflowMetaOption,
  type WorkflowMetaVO,
  type WorkflowRunVO,
  type WorkflowStep,
  type WorkflowStepType,
  type WorkflowVO
} from '@/api/workflows'
import {
  ArrowDown,
  ArrowUp,
  Copy,
  GitBranch,
  Play,
  Plus,
  RefreshCw,
  Search,
  Trash2
} from 'lucide-vue-next'

interface WorkflowRecord extends WorkflowVO {
  steps: WorkflowStep[]
}

type RunResult = Record<string, any>

const message = useMessage()
const projectStore = useProjectStore()
const providerStore = useModelProviderStore()
const assetStore = useAssetStore()

const loading = ref(false)
const executing = ref(false)
const searchQuery = ref('')
const showEditor = ref(false)
const editorMode = ref<'create' | 'edit'>('create')

const workflows = ref<WorkflowRecord[]>([])
const selectedWorkflow = ref<WorkflowRecord | null>(null)
const runs = ref<WorkflowRunVO[]>([])

const editForm = reactive({
  id: null as number | null,
  name: '',
  description: ''
})
const stepDrafts = ref<WorkflowStep[]>([])
const workflowMeta = ref<WorkflowMetaVO>(emptyWorkflowMeta())
const generationMeta = ref<GenerationMetaVO>({})

const stepTypeOptions = computed(() => workflowMeta.value.stepTypes || [])
const imageSizeOptions = computed(() => workflowMeta.value.imageSizes || [])
const videoDurationOptions = computed(() => workflowMeta.value.videoDurations || [])
const subtitleLanguageOptions = computed(() => workflowMeta.value.subtitleLanguages || [])
const allowedImageProviderTypes = computed(() => generationMeta.value.image?.allowedProviderTypes || [])
const allowedVideoProviderTypes = computed(() => generationMeta.value.video?.allowedProviderTypes || [])
const defaultImageSize = computed(() => workflowMeta.value.defaults?.imageSize || imageSizeOptions.value[0]?.value || '')
const defaultVideoDuration = computed(() => workflowMeta.value.defaults?.videoDuration || videoDurationOptions.value[0]?.value || '')
const defaultSubtitleLanguage = computed(() => workflowMeta.value.defaults?.subtitleLanguage || subtitleLanguageOptions.value[0]?.value || '')

const filteredWorkflows = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  const currentProjectId = projectStore.activeProjectId
  return workflows.value.filter(item => {
    const matchesProject = item.projectId === currentProjectId
    if (!matchesProject) return false
    if (!query) return true
    return [item.name, item.description]
      .some(value => String(value || '').toLowerCase().includes(query))
  })
})

// 前端分页(workflows 全量不动)
const wfPage = ref(1)
const wfPageSize = 10
const pagedWorkflows = computed(() => {
  const start = (wfPage.value - 1) * wfPageSize
  return filteredWorkflows.value.slice(start, start + wfPageSize)
})
watch([searchQuery, () => projectStore.activeProjectId], () => { wfPage.value = 1 })

const imageAssetOptions = computed(() => {
  return assetStore
    .getAssetsByProject(projectStore.activeProjectId)
    .filter(item => item.assetType === 'image' || item.assetType === 'reference')
    .map(item => ({
      label: `${item.fileName} (#${item.id})`,
      value: item.id
    }))
})

function providerOptions(type: WorkflowStepType) {
  const providers = providerStore.getProvidersByProject(projectStore.activeProjectId)
  const allowedTypes = type === 'image'
    ? allowedImageProviderTypes.value
    : type === 'video'
      ? allowedVideoProviderTypes.value
      : []
  return providers
    .filter(provider => allowedTypes.includes(provider.type))
    .map(provider => ({
      label: `${provider.name} (#${provider.id})`,
      value: provider.id
    }))
}

function stepTypeColor(type: string) {
  const map: Record<string, 'success' | 'warning' | 'info' | 'default'> = {
    image: 'success',
    video: 'warning',
    subtitle: 'info'
  }
  return map[type] || 'default'
}

function stepTypeLabel(type: string) {
  const map: Record<string, string> = {
    image: '生图',
    video: '生视频',
    subtitle: '字幕'
  }
  return map[type] || type || '未知步骤'
}

function runStatusType(status?: string) {
  if (status === 'success') return 'success'
  if (status === 'running') return 'warning'
  return 'error'
}

function runStatusLabel(status?: string) {
  if (status === 'success') return '成功'
  if (status === 'running') return '运行中'
  if (status === 'failed') return '失败'
  return status || '未知'
}

function workflowTypeSummary(steps: WorkflowStep[]) {
  return steps.map(step => stepTypeLabel(step.type)).join(' → ')
}

function formatRunTime(run: WorkflowRunVO) {
  const value = run.finishedAt || run.startedAt || run.createdAt || ''
  return String(value).replace('T', ' ').substring(0, 19) || '--'
}

function stepPromptPlaceholder(type: WorkflowStepType) {
  if (type === 'image') return '描述这一张图要生成什么'
  if (type === 'video') return '描述视频镜头、动态或转场'
  return '可选，补充品牌词、人名或术语，帮助转写更准确'
}

function emptyWorkflowMeta(): WorkflowMetaVO {
  return {
    stepTypes: [],
    imageSizes: [],
    videoDurations: [],
    subtitleLanguages: [],
    defaults: {}
  }
}

function resolveOptionValue(options: WorkflowMetaOption[], preferredValue: string, fallbackValue = '') {
  if (preferredValue && options.some(option => option.value === preferredValue)) {
    return preferredValue
  }
  return options[0]?.value || fallbackValue
}

function defaultStep(type: WorkflowStepType = 'image'): WorkflowStep {
  if (type === 'video') {
    return {
      type,
      prompt: '',
      providerId: null,
      modelName: '',
      duration: defaultVideoDuration.value || undefined,
      sourceAssetId: null,
      endAssetId: null
    }
  }
  if (type === 'subtitle') {
    return {
      type,
      prompt: '',
      language: defaultSubtitleLanguage.value || undefined,
      voice: false
    }
  }
  return {
    type,
      prompt: '',
      providerId: null,
      modelName: '',
      negativePrompt: '',
      size: defaultImageSize.value || undefined,
      count: 1,
      usePreviousAsReference: false
    }
  }

function sanitizeStep(step: WorkflowStep): WorkflowStep {
  return JSON.parse(JSON.stringify(step))
}

function normalizeStep(step: WorkflowStep) {
  const normalized = defaultStep(step.type)
  Object.assign(step, normalized, step)
}

function parseStepsJson(stepsJson: string): WorkflowStep[] {
  try {
    const parsed = JSON.parse(stepsJson)
    if (!Array.isArray(parsed)) return []
    return parsed.map((item: any) => {
      const type = (item.type || 'image') as WorkflowStepType
      return { ...defaultStep(type), ...item, type }
    })
  } catch {
    return []
  }
}

function normalizeWorkflow(raw: WorkflowVO): WorkflowRecord {
  return {
    ...raw,
    steps: parseStepsJson(raw.stepsJson || '[]')
  }
}

async function loadPageData() {
  loading.value = true
  try {
    await Promise.allSettled([
      loadWorkflowMeta(),
      loadGenerationMeta(),
      providerStore.refresh(projectStore.activeProjectId),
      assetStore.refresh(),
      loadWorkflows()
    ])
  } finally {
    loading.value = false
  }
}

async function loadGenerationMeta() {
  try {
    const res = await generationApi.getMeta()
    generationMeta.value = ((res as any).data || {}) as GenerationMetaVO
  } catch {
    generationMeta.value = {}
  }
}

async function loadWorkflowMeta() {
  try {
    const res = await workflowApi.meta()
    const data = (res as any).data || {}
    workflowMeta.value = {
      stepTypes: Array.isArray(data.stepTypes) ? data.stepTypes : [],
      imageSizes: Array.isArray(data.imageSizes) ? data.imageSizes : [],
      videoDurations: Array.isArray(data.videoDurations) ? data.videoDurations : [],
      subtitleLanguages: Array.isArray(data.subtitleLanguages) ? data.subtitleLanguages : [],
      defaults: data.defaults && typeof data.defaults === 'object' ? data.defaults : {}
    }
  } catch {
    workflowMeta.value = emptyWorkflowMeta()
  }
}

async function loadWorkflows() {
  try {
    const res = await workflowApi.list(projectStore.activeProjectId)
    workflows.value = ((res as any).data || []).map((item: WorkflowVO) => normalizeWorkflow(item))
    if (selectedWorkflow.value) {
      const next = workflows.value.find(item => item.id === selectedWorkflow.value?.id) || null
      selectedWorkflow.value = next
      if (next) {
        await loadRuns(next.id)
      }
    } else if (workflows.value.length > 0) {
      await selectWorkflow(workflows.value[0])
    }
  } catch {
    workflows.value = []
  }
}

async function loadRuns(workflowId: number) {
  try {
    const res = await workflowApi.listRuns(workflowId)
    runs.value = (res as any).data || []
  } catch {
    runs.value = []
  }
}

async function selectWorkflow(workflow: WorkflowRecord) {
  selectedWorkflow.value = workflow
  await loadRuns(workflow.id)
}

function resetEditor() {
  editForm.id = null
  editForm.name = ''
  editForm.description = ''
  stepDrafts.value = [defaultStep('image')]
}

function openCreateEditor() {
  editorMode.value = 'create'
  resetEditor()
  showEditor.value = true
}

function openEditEditor(workflow: WorkflowRecord) {
  editorMode.value = 'edit'
  editForm.id = workflow.id
  editForm.name = workflow.name
  editForm.description = workflow.description || ''
  stepDrafts.value = workflow.steps.map(step => sanitizeStep(step))
  showEditor.value = true
}

function applyTemplate(template: 'image-only' | 'image-to-video' | 'full-pipeline') {
  const coverImageSize = resolveOptionValue(imageSizeOptions.value, '1024x1024', defaultImageSize.value)
  const wideImageSize = resolveOptionValue(imageSizeOptions.value, '1536x1024', defaultImageSize.value)
  const shortVideoDuration = resolveOptionValue(videoDurationOptions.value, '5s', defaultVideoDuration.value)
  const chineseSubtitleLanguage = resolveOptionValue(subtitleLanguageOptions.value, 'zh', defaultSubtitleLanguage.value)
  if (template === 'image-only') {
    stepDrafts.value = [
      {
        ...defaultStep('image'),
        prompt: '生成一张高质量封面主视觉',
        size: coverImageSize || undefined,
        count: 1
      }
    ]
    return
  }
  if (template === 'image-to-video') {
    stepDrafts.value = [
      {
        ...defaultStep('image'),
        prompt: '生成一张适合转视频的主视觉画面',
        size: wideImageSize || undefined,
        count: 1
      },
      {
        ...defaultStep('video'),
        prompt: '将上一步的图片转成具有镜头推进感的短视频',
        duration: shortVideoDuration || undefined
      }
    ]
    return
  }
  stepDrafts.value = [
    {
      ...defaultStep('image'),
      prompt: '生成一张适合品牌宣传的主视觉画面',
      size: wideImageSize || undefined,
      count: 1
    },
    {
      ...defaultStep('video'),
      prompt: '把上一步主视觉制作成具有动态镜头和光效的短片',
      duration: shortVideoDuration || undefined
    },
    {
      ...defaultStep('subtitle'),
      prompt: '',
      language: chineseSubtitleLanguage || undefined,
      voice: true
    }
  ]
}

function addStep(type: WorkflowStepType = 'image') {
  stepDrafts.value.push(defaultStep(type))
}

function removeStep(index: number) {
  if (stepDrafts.value.length === 1) {
    message.warning('至少保留一个步骤')
    return
  }
  stepDrafts.value.splice(index, 1)
}

function moveStep(index: number, direction: -1 | 1) {
  const nextIndex = index + direction
  if (nextIndex < 0 || nextIndex >= stepDrafts.value.length) return
  const cloned = [...stepDrafts.value]
  const [step] = cloned.splice(index, 1)
  cloned.splice(nextIndex, 0, step)
  stepDrafts.value = cloned
}

async function handleSave() {
  if (!editForm.name.trim()) {
    message.error('请填写工作流名称')
    return
  }
  if (stepDrafts.value.length === 0) {
    message.error('至少需要一个步骤')
    return
  }
  for (const [index, step] of stepDrafts.value.entries()) {
    if (!step.prompt.trim() && step.type !== 'subtitle') {
      message.error(`步骤 ${index + 1} 需要填写 Prompt`)
      return
    }
    if ((step.type === 'image' || step.type === 'video') && !step.providerId) {
      message.error(`步骤 ${index + 1} 需要选择模型提供商`)
      return
    }
  }

  const payload = {
    projectId: projectStore.activeProjectId,
    name: editForm.name.trim(),
    description: editForm.description.trim(),
    stepsJson: JSON.stringify(stepDrafts.value.map(step => sanitizeStep(step)))
  }

  try {
    if (editorMode.value === 'edit' && editForm.id) {
      await workflowApi.update(editForm.id, payload)
      message.success('工作流已更新')
    } else {
      await workflowApi.create(payload)
      message.success('工作流已创建')
    }
    showEditor.value = false
    await loadWorkflows()
  } catch (err: any) {
    message.error(err.message || '保存失败')
  }
}

async function handleDelete(id: number) {
  try {
    await workflowApi.remove(id)
    if (selectedWorkflow.value?.id === id) {
      selectedWorkflow.value = null
      runs.value = []
    }
    workflows.value = workflows.value.filter(item => item.id !== id)
    message.success('工作流已删除')
  } catch (err: any) {
    message.error(err.message || '删除失败')
  }
}

async function cloneWorkflow(workflow: WorkflowRecord) {
  try {
    const payload = {
      projectId: projectStore.activeProjectId,
      name: `${workflow.name} (副本)`,
      description: workflow.description || '',
      stepsJson: JSON.stringify(workflow.steps.map(step => sanitizeStep(step)))
    }
    await workflowApi.create(payload)
    message.success('工作流已克隆')
    await loadWorkflows()
  } catch (err: any) {
    message.error(err.message || '克隆失败')
  }
}

async function handleExecute() {
  if (!selectedWorkflow.value?.id) return
  executing.value = true
  try {
    const res = await workflowApi.execute(selectedWorkflow.value.id)
    const run = (res as any).data
    if (run) {
      message.success('工作流执行完成')
      await loadRuns(selectedWorkflow.value.id)
      await assetStore.refresh()
    }
  } catch (err: any) {
    message.error(err.message || '执行失败')
    if (selectedWorkflow.value?.id) {
      await loadRuns(selectedWorkflow.value.id)
    }
  } finally {
    executing.value = false
  }
}

function parseRunResults(run: WorkflowRunVO): RunResult[] {
  if (!run.stepsResultJson) return []
  try {
    const parsed = JSON.parse(run.stepsResultJson)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

watch(() => projectStore.activeProjectId, async () => {
  selectedWorkflow.value = null
  runs.value = []
  await loadPageData()
})

onMounted(async () => {
  await loadPageData()
})
</script>

<style scoped>
.workflows-container {
  padding-bottom: 40px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 6px 0;
  color: var(--text-primary);
}

.subtitle {
  font-size: 13px;
  color: #9ca3af;
  margin: 0;
}

.glass-card {
  background: rgba(15, 23, 42, 0.4) !important;
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  border-radius: 16px !important;
}

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.detail-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-head small {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 400;
}

.inline-icon {
  width: 14px;
  height: 14px;
  color: var(--text-muted);
}

.wf-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pager { display: flex; justify-content: center; margin-top: 14px; }

.wf-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background .2s ease, border-color .2s ease, transform .2s ease;
}

.wf-card:hover {
  background: rgba(255, 255, 255, 0.03);
  border-color: rgba(255, 255, 255, 0.07);
  transform: translateY(-1px);
}

.wf-card.active-wf {
  background: rgba(16, 185, 129, 0.08);
  border-color: rgba(16, 185, 129, 0.25);
}

.wf-icon-box {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(59, 130, 246, 0.14);
}

.wf-icon {
  width: 18px;
  height: 18px;
  color: #60a5fa;
}

.wf-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.wf-name {
  font-size: 14px;
  font-weight: 600;
  color: #f8fafc;
}

.wf-meta,
.wf-desc {
  font-size: 12px;
  color: #94a3b8;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.summary-chip {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.summary-chip span {
  font-size: 11px;
  color: #94a3b8;
}

.summary-chip strong {
  font-size: 14px;
  color: #f8fafc;
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.step-card,
.editor-step-card {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(255, 255, 255, 0.02);
}

.step-card {
  display: flex;
  gap: 12px;
}

.step-index,
.step-no {
  font-weight: 700;
  color: #60a5fa;
}

.step-index {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(59, 130, 246, 0.15);
  flex-shrink: 0;
}

.step-body {
  flex: 1;
}

.step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.step-desc {
  font-size: 13px;
  color: #e2e8f0;
}

.step-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 11px;
  color: #94a3b8;
}

.runs-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.run-card {
  padding: 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.run-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 8px;
}

.run-head-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.run-time,
.run-step {
  font-size: 12px;
  color: #94a3b8;
}

.run-error {
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(239, 68, 68, 0.08);
  color: #fca5a5;
  font-size: 12px;
  margin-bottom: 8px;
}

.run-results {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.run-result-item {
  padding: 10px;
  border-radius: 10px;
  background: rgba(2, 6, 23, 0.45);
}

.run-result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #e2e8f0;
}

.run-result-body {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 11px;
  color: #94a3b8;
}

.steps-template-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
  font-size: 12px;
  color: #9ca3af;
}

.editor-steps {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.editor-step-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.editor-step-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-tip {
  font-size: 12px;
  color: var(--text-muted);
}

.beginner-tip {
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(59, 130, 246, 0.08);
  border: 1px solid rgba(59, 130, 246, 0.14);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.add-step-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.add-step-btn {
  width: 100%;
}

@media (max-width: 1200px) {
  .summary-strip {
    grid-template-columns: 1fr;
  }

  .add-step-actions {
    grid-template-columns: 1fr;
  }
}
</style>
