<template>
  <div class="workflows-container">
    <div class="page-header">
      <div>
        <h2>工作流编排 (Workflows)</h2>
        <p class="subtitle">把图像生成、视频生成和字幕处理编成可复用链路，减少重复操作。</p>
        <p v-if="collaborationNotice" class="scope-notice">{{ collaborationNotice }}</p>
      </div>
      <n-space>
        <n-button secondary :loading="loading" @click="loadPageData">
          <template #icon><RefreshCw /></template>
          刷新
        </n-button>
        <n-button type="primary" :disabled="!canOperateCurrentProject" @click="openCreateEditor">
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
              <span>{{ workflowListLabel }} ({{ workflowCountDisplay }})</span>
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

          <div v-if="loading && filteredWorkflows === null" class="loading-box">
            <n-spin size="small" />
          </div>
          <div v-else-if="filteredWorkflows && filteredWorkflows.length > 0" class="wf-list">
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
              <n-button v-if="canOperateCurrentProject" size="tiny" type="error" tertiary @click.stop="handleDelete(wf.id)">
                <template #icon><Trash2 /></template>
              </n-button>
            </div>
          </div>
          <n-empty v-else-if="filteredWorkflows !== null" description="当前项目还没有工作流" />
          <n-empty v-else description="工作流数据待确认，请稍后重试。" />
          <div class="pager" v-if="(filteredWorkflows?.length || 0) > wfPageSize">
            <n-pagination v-model:page="wfPage" :page-size="wfPageSize" :item-count="filteredWorkflows?.length || 0" simple />
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
                <n-button type="warning" :loading="executing" :disabled="!canOperateCurrentProject" @click="handleExecute">
                  <template #icon><Play /></template>
                  执行工作流
                </n-button>
                <n-button secondary :disabled="!canOperateCurrentProject" @click="cloneWorkflow(selectedWorkflow)">
                  <template #icon><Copy /></template>克隆
                </n-button>
                <n-button secondary :disabled="!canOperateCurrentProject" @click="openEditEditor(selectedWorkflow)">编辑</n-button>
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
              <strong>{{ latestRunStatus }}</strong>
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
              <div v-if="runsLoading && runs === null" class="loading-box">
                <n-spin size="small" />
              </div>
              <div v-else-if="runs && runs.length > 0" class="runs-list">
                <div v-for="run in runs" :key="run.id" class="run-card">
                  <div class="run-head">
                    <div class="run-head-left">
                      <n-tag size="small" :type="runStatusType(run.status)">{{ runStatusLabel(run.status) }}</n-tag>
                      <span class="run-time">{{ formatRunTime(run) }}</span>
                    </div>
                    <span class="run-step">{{ formatRunProgress(run) }}</span>
                  </div>

                  <div v-if="run.errorMessage" class="run-error">
                    {{ run.errorMessage }}
                  </div>

                  <div v-if="parseRunResults(run).length > 0" class="run-results">
                    <div v-for="(result, idx) in parseRunResults(run)" :key="`${run.id}-${result.stepIndex ?? 'unknown'}-${idx}`" class="run-result-item">
                      <div class="run-result-head">
                        <span>{{ formatRunResultStep(result.stepIndex) }} · {{ stepTypeLabel(result.stepType || '') }}</span>
                        <n-tag size="tiny" :type="result.status === 'success' ? 'success' : 'error'">{{ result.status === 'success' ? '成功' : '失败' }}</n-tag>
                      </div>
                      <div class="run-result-body">
                        <span v-if="result.message">{{ result.message }}</span>
                        <span v-if="result.taskId">任务 #{{ result.taskId }}</span>
                        <span v-if="result.assetId">资产 #{{ result.assetId }}</span>
                        <span v-if="result.sourceAssetId">来源资产 #{{ result.sourceAssetId }}</span>
                        <span v-if="result.referenceAssetId">参考资产 #{{ result.referenceAssetId }}</span>
                        <span v-if="result.subtitleId">字幕 #{{ result.subtitleId }}</span>
                        <span v-if="result.voiceUrl">已生成配音</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <n-empty v-else-if="runs !== null" description="暂无运行记录" style="padding: 12px 0;" />
              <n-empty v-else description="运行记录待确认，请稍后重试。" style="padding: 12px 0;" />
            </n-collapse-item>
          </n-collapse>
        </n-card>

        <n-card v-else class="glass-card" :bordered="false">
          <n-empty :description="workflows === null ? '工作流数据待确认，请稍后重试。' : '从左侧选择一个工作流，或新建一条自动化创作链路'" />
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

        <div v-if="editorStatusMessages.length > 0" class="editor-status-list">
          <div
            v-for="status in editorStatusMessages"
            :key="status.key"
            class="editor-status"
            :class="`editor-status--${status.tone}`"
          >
            {{ status.text }}
          </div>
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
                  <n-select
                    v-model:value="step.type"
                    :options="stepTypeOptions"
                    :disabled="stepTypeSelectDisabled"
                    :placeholder="stepTypeSelectPlaceholder"
                    @update:value="() => normalizeStep(step)"
                  />
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
                    <n-select
                      v-model:value="step.providerId"
                      :options="providerOptions(step.type)"
                      :disabled="providerSelectDisabled(step.type)"
                      :placeholder="providerSelectPlaceholder(step.type)"
                    />
                  </n-form-item>
                </n-col>
                <n-col :span="8">
                  <n-form-item label="模型名称">
                    <n-input v-model:value="step.modelName" placeholder="可留空，走提供商默认模型" />
                  </n-form-item>
                </n-col>
                <n-col :span="8" v-if="step.type === 'image'">
                  <n-form-item label="输出尺寸">
                    <n-select
                      v-model:value="step.size"
                      :options="imageSizeOptions"
                      :disabled="imageSizeSelectDisabled"
                      :placeholder="imageSizeSelectPlaceholder"
                    />
                  </n-form-item>
                </n-col>
                <n-col :span="8" v-else>
                  <n-form-item label="视频时长">
                    <n-select
                      v-model:value="step.duration"
                      :options="videoDurationOptions"
                      :disabled="videoDurationSelectDisabled"
                      :placeholder="videoDurationSelectPlaceholder"
                    />
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
                      :disabled="assetSelectDisabled"
                      :placeholder="assetSelectDisabled ? '参考素材待确认' : '留空则默认使用上一步图片资产'"
                    />
                  </n-form-item>
                </n-col>
                <n-col :span="12">
                  <n-form-item label="尾帧来源">
                    <n-select
                      v-model:value="step.endAssetId"
                      clearable
                      :options="imageAssetOptions"
                      :disabled="assetSelectDisabled"
                      :placeholder="assetSelectDisabled ? '参考素材待确认' : '可选，指定尾帧资产'"
                    />
                  </n-form-item>
                </n-col>
              </n-row>
            </template>

            <template v-if="step.type === 'subtitle'">
              <n-row :gutter="12">
                <n-col :span="12">
                  <n-form-item label="字幕语言">
                    <n-select
                      v-model:value="step.language"
                      :options="subtitleLanguageOptions"
                      :disabled="subtitleLanguageSelectDisabled"
                      :placeholder="subtitleLanguageSelectPlaceholder"
                    />
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
            :disabled="stepTypeSelectDisabled"
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

type RunResult = {
  stepIndex?: number
  stepType?: string
  status?: string
} & Record<string, unknown>
type LoadState = 'loading' | 'ready' | 'error'
type EditorStatusTone = 'error' | 'info'

const message = useMessage()
const projectStore = useProjectStore()
const providerStore = useModelProviderStore()
const assetStore = useAssetStore()

const loading = ref(true)
const executing = ref(false)
const runsLoading = ref(false)
const searchQuery = ref('')
const showEditor = ref(false)
const editorMode = ref<'create' | 'edit'>('create')

const workflows = ref<WorkflowRecord[] | null>(null)
const selectedWorkflow = ref<WorkflowRecord | null>(null)
const runs = ref<WorkflowRunVO[] | null>(null)
const workflowMetaLoadState = ref<LoadState>('loading')
const generationMetaLoadState = ref<LoadState>('loading')
const providerLoadState = ref<LoadState>('loading')
const assetLibraryLoadState = ref<LoadState>('loading')
const currentProject = computed(() =>
  projectStore.projects.find(project => project.id === projectStore.activeProjectId) || null
)
const canOperateCurrentProject = computed(() => !!currentProject.value)
const workflowListLabel = computed(() =>
  currentProject.value?.ownedByCurrentUser === false ? '当前协作项目工作流' : '当前项目工作流'
)
const collaborationNotice = computed(() => {
  if (!currentProject.value || currentProject.value.ownedByCurrentUser) {
    return ''
  }
  if (currentProject.value.accessPermission === 'view') {
    return '当前打开的是共享查看项目。根据后端真实权限，你仍可在这里新建、编辑、克隆、删除和执行工作流；这些改动会直接作用到当前项目，若缺少可用 Provider，请先联系具备管理权限的成员在模型配置页接入。'
  }
  return `当前打开的是共享${formatProjectPermissionLabel(currentProject.value.accessPermission)}项目，这里的工作流编排、执行与运行历史都会直接作用到该项目。`
})

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
const imageProviderOptionItems = computed(() => getProviderOptions('image'))
const videoProviderOptionItems = computed(() => getProviderOptions('video'))
const defaultImageSize = computed(() => workflowMeta.value.defaults?.imageSize || imageSizeOptions.value[0]?.value || '')
const defaultVideoDuration = computed(() => workflowMeta.value.defaults?.videoDuration || videoDurationOptions.value[0]?.value || '')
const defaultSubtitleLanguage = computed(() => workflowMeta.value.defaults?.subtitleLanguage || subtitleLanguageOptions.value[0]?.value || '')
const stepTypeSelectDisabled = computed(() => {
  return workflowMetaLoadState.value === 'error' || (workflowMetaLoadState.value === 'ready' && stepTypeOptions.value.length === 0)
})
const stepTypeSelectPlaceholder = computed(() => {
  if (workflowMetaLoadState.value === 'error') {
    return '步骤模板待确认'
  }
  if (stepTypeOptions.value.length === 0) {
    return '暂无步骤模板'
  }
  return '选择步骤类型'
})
const imageSizeSelectDisabled = computed(() => {
  return workflowMetaLoadState.value === 'error' || imageSizeOptions.value.length === 0
})
const imageSizeSelectPlaceholder = computed(() => {
  if (workflowMetaLoadState.value === 'error') {
    return '尺寸配置待确认'
  }
  if (imageSizeOptions.value.length === 0) {
    return '暂无尺寸选项'
  }
  return '选择输出尺寸'
})
const videoDurationSelectDisabled = computed(() => {
  return workflowMetaLoadState.value === 'error' || videoDurationOptions.value.length === 0
})
const videoDurationSelectPlaceholder = computed(() => {
  if (workflowMetaLoadState.value === 'error') {
    return '时长配置待确认'
  }
  if (videoDurationOptions.value.length === 0) {
    return '暂无时长选项'
  }
  return '选择视频时长'
})
const subtitleLanguageSelectDisabled = computed(() => {
  return workflowMetaLoadState.value === 'error' || subtitleLanguageOptions.value.length === 0
})
const subtitleLanguageSelectPlaceholder = computed(() => {
  if (workflowMetaLoadState.value === 'error') {
    return '字幕语言待确认'
  }
  if (subtitleLanguageOptions.value.length === 0) {
    return '暂无字幕语言'
  }
  return '选择字幕语言'
})
const assetSelectDisabled = computed(() => assetLibraryLoadState.value === 'error')
const editorStatusMessages = computed<Array<{ key: string; text: string; tone: EditorStatusTone }>>(() => {
  const messages: Array<{ key: string; text: string; tone: EditorStatusTone }> = []

  if (workflowMetaLoadState.value === 'error') {
    messages.push({ key: 'workflow-meta-error', text: '工作流步骤模板待确认，请稍后重试。', tone: 'error' })
  } else if (workflowMetaLoadState.value === 'ready' && stepTypeOptions.value.length === 0) {
    messages.push({ key: 'workflow-meta-empty', text: '当前没有可用的步骤模板，请先检查后台配置。', tone: 'info' })
  }

  if (generationMetaLoadState.value === 'error') {
    messages.push({ key: 'generation-meta-error', text: '图像/视频生成参数待确认，请稍后重试。', tone: 'error' })
  }

  if (providerLoadState.value === 'error') {
    messages.push({ key: 'provider-error', text: 'Provider 选项待确认，请稍后重试。', tone: 'error' })
  } else if (providerLoadState.value === 'ready') {
    if (allowedImageProviderTypes.value.length > 0 && imageProviderOptionItems.value.length === 0) {
      messages.push({ key: 'image-provider-empty', text: '当前项目暂无可用的生图 Provider。', tone: 'info' })
    }
    if (allowedVideoProviderTypes.value.length > 0 && videoProviderOptionItems.value.length === 0) {
      messages.push({ key: 'video-provider-empty', text: '当前项目暂无可用的生视频 Provider。', tone: 'info' })
    }
  }

  if (assetLibraryLoadState.value === 'error') {
    messages.push({ key: 'asset-library-error', text: '参考素材待确认，请稍后重试。', tone: 'error' })
  }

  return messages
})

const filteredWorkflows = computed<WorkflowRecord[] | null>(() => {
  if (workflows.value === null) {
    return null
  }
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
const workflowCountDisplay = computed(() => filteredWorkflows.value === null ? '-' : filteredWorkflows.value.length)
const latestRunStatus = computed(() => {
  if (runs.value === null) {
    return '待确认'
  }
  return runs.value[0] ? formatRunProgress(runs.value[0]) : '暂无'
})

function formatProjectPermissionLabel(permission?: string) {
  if (permission === 'owner') return '所有者'
  if (permission === 'admin') return '管理'
  if (permission === 'edit') return '编辑'
  return '查看'
}

function ensureActiveProjectSelected(actionText: string) {
  if (!projectStore.activeProjectId) {
    message.error(`请先选择一个项目空间后再${actionText}`)
    return false
  }
  return true
}

// 前端分页(workflows 全量不动)
const wfPage = ref(1)
const wfPageSize = 10
const pagedWorkflows = computed(() => {
  if (!filteredWorkflows.value) {
    return []
  }
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

function providerOptions(type: string) {
  if (type === 'image') {
    return imageProviderOptionItems.value
  }
  if (type === 'video') {
    return videoProviderOptionItems.value
  }
  return []
}

function getProviderOptions(type: 'image' | 'video') {
  const providers = providerStore.getProvidersForProject(projectStore.activeProjectId)
  const allowedTypes = type === 'image' ? allowedImageProviderTypes.value : allowedVideoProviderTypes.value
  return providers
    .filter(provider => allowedTypes.includes(provider.type))
    .map(provider => ({
      label: `${provider.name} (#${provider.id})`,
      value: provider.id
    }))
}

function providerSelectDisabled(type: string) {
  if (providerLoadState.value === 'error' || generationMetaLoadState.value === 'error') {
    return true
  }
  return providerOptions(type).length === 0
}

function providerSelectPlaceholder(type: string) {
  if (providerLoadState.value === 'error') {
    return 'Provider 选项待确认'
  }
  if (generationMetaLoadState.value === 'error') {
    return '生成参数待确认'
  }
  if (providerOptions(type).length === 0) {
    return `暂无可用${stepTypeLabel(type)} Provider`
  }
  return '选择 Provider'
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

function stepPromptPlaceholder(type: string) {
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

function defaultStep(type: WorkflowStepType | string = 'image'): WorkflowStep {
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
  if (type !== 'image') {
    return {
      type,
      prompt: ''
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

function normalizeComparableValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(item => normalizeComparableValue(item))
  }
  if (value && typeof value === 'object') {
    return Object.keys(value as Record<string, unknown>)
      .sort()
      .reduce<Record<string, unknown>>((result, key) => {
        result[key] = normalizeComparableValue((value as Record<string, unknown>)[key])
        return result
      }, {})
  }
  return value ?? null
}

function normalizeComparableWorkflowStep(step: WorkflowStep) {
  return normalizeComparableValue(sanitizeStep(step))
}

function assertWorkflowMatchesExpected(
  workflow: WorkflowRecord,
  expected: { projectId: number; name: string; description: string; steps: WorkflowStep[] },
  errorMessage: string
) {
  if (workflow.projectId !== expected.projectId) {
    throw new Error(errorMessage)
  }
  if (workflow.name !== expected.name) {
    throw new Error(errorMessage)
  }
  if (String(workflow.description || '') !== expected.description) {
    throw new Error(errorMessage)
  }
  if (workflow.steps.length !== expected.steps.length) {
    throw new Error(errorMessage)
  }
  const actualSignature = JSON.stringify(workflow.steps.map(step => normalizeComparableWorkflowStep(step)))
  const expectedSignature = JSON.stringify(expected.steps.map(step => normalizeComparableWorkflowStep(step)))
  if (actualSignature !== expectedSignature) {
    throw new Error(errorMessage)
  }
}

function assertWorkflowRunMatchesExpected(
  run: WorkflowRunVO,
  expected: { workflowId: number; projectId: number },
  errorMessage: string
) {
  if (run.workflowId !== expected.workflowId || run.projectId !== expected.projectId) {
    throw new Error(errorMessage)
  }
  if (run.status === 'failed') {
    throw new Error(run.errorMessage || '工作流执行失败')
  }
  if (run.status !== 'success' && run.status !== 'running') {
    throw new Error(errorMessage)
  }
  if (run.status === 'running' && typeof run.currentStep === 'number' && run.currentStep < 0) {
    throw new Error(errorMessage)
  }
}

function normalizeStep(step: WorkflowStep) {
  const normalized = defaultStep(step.type)
  Object.assign(step, normalized, step)
}

function parseStepsJson(stepsJson: string): WorkflowStep[] {
  try {
    const parsed = JSON.parse(stepsJson)
    if (!Array.isArray(parsed)) return []
    return parsed.map((item: unknown) => {
      const record = isPlainObject(item) ? item : {}
      const type = typeof record.type === 'string' ? record.type.trim() : ''
      const base = defaultStep(type)
      return { ...base, ...record, type }
    })
  } catch {
    return []
  }
}

function normalizeWorkflow(raw: unknown): WorkflowRecord {
  if (!isPlainObject(raw)) {
    throw new Error('工作流结果待确认')
  }
  if (typeof raw.name !== 'string' || typeof raw.stepsJson !== 'string') {
    throw new Error('工作流结果待确认')
  }
  return {
    id: parseRequiredNumber(raw.id, '工作流结果待确认'),
    projectId: parseRequiredNumber(raw.projectId, '工作流结果待确认'),
    name: raw.name,
    description: typeof raw.description === 'string' ? raw.description : undefined,
    stepsJson: raw.stepsJson,
    status: parseRequiredNumber(raw.status, '工作流结果待确认'),
    createdAt: typeof raw.createdAt === 'string' ? raw.createdAt : undefined,
    steps: parseStepsJson(raw.stepsJson)
  }
}

function normalizeRun(raw: unknown): WorkflowRunVO {
  if (!isPlainObject(raw) || typeof raw.status !== 'string') {
    throw new Error('工作流运行结果待确认')
  }
  return {
    id: parseRequiredNumber(raw.id, '工作流运行结果待确认'),
    workflowId: parseRequiredNumber(raw.workflowId, '工作流运行结果待确认'),
    projectId: parseRequiredNumber(raw.projectId, '工作流运行结果待确认'),
    status: raw.status,
    currentStep: raw.currentStep == null ? undefined : parseRequiredNumber(raw.currentStep, '工作流运行结果待确认'),
    stepsResultJson: typeof raw.stepsResultJson === 'string' ? raw.stepsResultJson : undefined,
    errorMessage: typeof raw.errorMessage === 'string' ? raw.errorMessage : undefined,
    startedAt: typeof raw.startedAt === 'string' ? raw.startedAt : undefined,
    finishedAt: typeof raw.finishedAt === 'string' ? raw.finishedAt : undefined,
    createdAt: typeof raw.createdAt === 'string' ? raw.createdAt : undefined
  }
}

function findWorkflowById(id: number) {
  return workflows.value?.find(item => item.id === id) || null
}

async function reloadWorkflowById(id: number, messageText = '工作流结果待确认') {
  await loadWorkflows()
  const confirmed = findWorkflowById(id)
  if (!confirmed) {
    throw new Error(messageText)
  }
  return confirmed
}

function parseRequiredNumber(value: unknown, errorMessage: string) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    throw new Error(errorMessage)
  }
  return parsed
}

function requireStringValue(value: unknown, errorMessage: string) {
  const normalized = typeof value === 'string' ? value.trim() : ''
  if (!normalized) {
    throw new Error(errorMessage)
  }
  return normalized
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string): unknown {
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

function requireWorkflowMetaOptionList(value: unknown, errorMessage: string) {
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

function requireGenerationMeta(value: unknown): GenerationMetaVO {
  const errorMessage = '图像/视频生成参数待确认'
  if (!isPlainObject(value) || !isPlainObject(value.image) || !isPlainObject(value.video)) {
    throw new Error(errorMessage)
  }
  const imageDefaults = value.image.defaults
  const videoDefaults = value.video.defaults
  if (
    imageDefaults != null && !isPlainObject(imageDefaults)
    || videoDefaults != null && !isPlainObject(videoDefaults)
  ) {
    throw new Error(errorMessage)
  }
  return {
    image: {
      allowedProviderTypes: requireAllowedProviderTypes(value.image.allowedProviderTypes, errorMessage),
      resolutionOptions: requireWorkflowMetaOptionList(value.image.resolutionOptions ?? [], errorMessage),
      qualityOptions: requireWorkflowMetaOptionList(value.image.qualityOptions ?? [], errorMessage),
      defaults: imageDefaults
        ? {
            resolution: typeof imageDefaults.resolution === 'string' ? imageDefaults.resolution.trim() : undefined,
            quality: typeof imageDefaults.quality === 'string' ? imageDefaults.quality.trim() : undefined
          }
        : undefined
    },
    video: {
      allowedProviderTypes: requireAllowedProviderTypes(value.video.allowedProviderTypes, errorMessage),
      durationOptions: requireWorkflowMetaOptionList(value.video.durationOptions ?? [], errorMessage),
      cameraMotionOptions: requireWorkflowMetaOptionList(value.video.cameraMotionOptions ?? [], errorMessage),
      defaults: videoDefaults
        ? {
            duration: typeof videoDefaults.duration === 'string' ? videoDefaults.duration.trim() : undefined,
            cameraMotion: typeof videoDefaults.cameraMotion === 'string' ? videoDefaults.cameraMotion.trim() : undefined
          }
        : undefined
    }
  }
}

function requireWorkflowMeta(value: unknown): WorkflowMetaVO {
  const errorMessage = '工作流步骤模板待确认'
  if (!isPlainObject(value)) {
    throw new Error(errorMessage)
  }
  const defaults = value.defaults
  if (defaults != null && !isPlainObject(defaults)) {
    throw new Error(errorMessage)
  }
  return {
    stepTypes: requireWorkflowMetaOptionList(value.stepTypes, errorMessage),
    imageSizes: requireWorkflowMetaOptionList(value.imageSizes, errorMessage),
    videoDurations: requireWorkflowMetaOptionList(value.videoDurations, errorMessage),
    subtitleLanguages: requireWorkflowMetaOptionList(value.subtitleLanguages, errorMessage),
    defaults: defaults
      ? {
          imageSize: typeof defaults.imageSize === 'string' ? defaults.imageSize.trim() : undefined,
          videoDuration: typeof defaults.videoDuration === 'string' ? defaults.videoDuration.trim() : undefined,
          subtitleLanguage: typeof defaults.subtitleLanguage === 'string' ? defaults.subtitleLanguage.trim() : undefined
        }
      : {}
  }
}

function normalizeWorkflowList(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('工作流数据待确认')
  }
  const normalized = value.map((item: unknown) => normalizeWorkflow(item))
  const seenIds = new Set<number>()
  normalized.forEach(item => {
    if (seenIds.has(item.id)) {
      throw new Error('工作流数据待确认')
    }
    seenIds.add(item.id)
  })
  return normalized
}

function normalizeRunList(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('工作流运行记录待确认')
  }
  const normalized = value.map((item: unknown) => normalizeRun(item))
  const seenIds = new Set<number>()
  normalized.forEach(item => {
    if (seenIds.has(item.id)) {
      throw new Error('工作流运行记录待确认')
    }
    seenIds.add(item.id)
  })
  return normalized
}

async function loadPageData() {
  loading.value = true
  try {
    await Promise.allSettled([
      loadWorkflowMeta(),
      loadGenerationMeta(),
      loadProviderOptions(),
      loadAssetLibrary(),
      loadWorkflows()
    ])
  } finally {
    loading.value = false
  }
}

async function loadGenerationMeta() {
  generationMetaLoadState.value = 'loading'
  try {
    const res = await generationApi.getMeta()
    generationMeta.value = requireGenerationMeta(getResponseData(res, '图像/视频生成参数待确认'))
    generationMetaLoadState.value = 'ready'
  } catch {
    generationMeta.value = {}
    generationMetaLoadState.value = 'error'
  }
}

async function loadWorkflowMeta() {
  workflowMetaLoadState.value = 'loading'
  try {
    const res = await workflowApi.meta()
    workflowMeta.value = requireWorkflowMeta(getResponseData(res, '工作流步骤模板待确认'))
    workflowMetaLoadState.value = 'ready'
  } catch {
    workflowMeta.value = emptyWorkflowMeta()
    workflowMetaLoadState.value = 'error'
  }
}

async function loadProviderOptions() {
  providerLoadState.value = 'loading'
  try {
    await providerStore.refresh(projectStore.activeProjectId)
    providerLoadState.value = 'ready'
  } catch {
    providerLoadState.value = 'error'
  }
}

async function loadAssetLibrary() {
  assetLibraryLoadState.value = 'loading'
  try {
    if (!projectStore.activeProjectId) {
      assetStore.clear()
      assetLibraryLoadState.value = 'ready'
      return
    }
    await assetStore.refresh({ projectId: projectStore.activeProjectId })
    assetLibraryLoadState.value = 'ready'
  } catch {
    assetLibraryLoadState.value = 'error'
  }
}

async function loadWorkflows() {
  try {
    const res = await workflowApi.list(projectStore.activeProjectId)
    const records = normalizeWorkflowList(getResponseData(res, '工作流数据待确认'))
    workflows.value = records
    if (selectedWorkflow.value) {
      const next = records.find((item: WorkflowRecord) => item.id === selectedWorkflow.value?.id) || null
      selectedWorkflow.value = next
      if (next) {
        await loadRuns(next.id)
      } else {
        runs.value = []
      }
    } else if (records.length > 0) {
      await selectWorkflow(records[0])
    } else {
      runs.value = []
    }
  } catch {
    workflows.value = null
    selectedWorkflow.value = null
    runs.value = null
  }
}

async function loadRuns(workflowId: number) {
  runsLoading.value = true
  runs.value = null
  try {
    const res = await workflowApi.listRuns(workflowId)
    runs.value = normalizeRunList(getResponseData(res, '工作流运行记录待确认'))
  } catch {
    runs.value = null
  } finally {
    runsLoading.value = false
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
  if (!ensureActiveProjectSelected('新建工作流')) {
    return
  }
  editorMode.value = 'create'
  resetEditor()
  showEditor.value = true
}

function openEditEditor(workflow: WorkflowRecord) {
  if (!ensureActiveProjectSelected('编辑工作流')) {
    return
  }
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
  if (!ensureActiveProjectSelected('保存工作流')) {
    return
  }
  if (!editForm.name.trim()) {
    message.error('请填写工作流名称')
    return
  }
  if (stepDrafts.value.length === 0) {
    message.error('至少需要一个步骤')
    return
  }
  if (stepDrafts.value.some(step => step.type === 'image' || step.type === 'video')) {
    if (generationMetaLoadState.value === 'error') {
      message.error('图像/视频生成参数待确认，请稍后重试')
      return
    }
    if (providerLoadState.value === 'error') {
      message.error('Provider 选项待确认，请稍后重试')
      return
    }
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
  const expectedWorkflow = {
    projectId: payload.projectId,
    name: payload.name,
    description: payload.description,
    steps: stepDrafts.value.map(step => sanitizeStep(step))
  }

  try {
    if (editorMode.value === 'edit' && editForm.id) {
      const res = await workflowApi.update(editForm.id, payload)
      const updated = normalizeWorkflow(getResponseData(res, '工作流更新结果待确认'))
      const confirmed = await reloadWorkflowById(updated.id, '工作流更新结果待确认')
      assertWorkflowMatchesExpected(confirmed, expectedWorkflow, '工作流更新结果待确认')
      selectedWorkflow.value = confirmed
      await loadRuns(confirmed.id)
      message.success('工作流已更新')
    } else {
      const res = await workflowApi.create(payload)
      const created = normalizeWorkflow(getResponseData(res, '工作流创建结果待确认'))
      const confirmed = await reloadWorkflowById(created.id, '工作流创建结果待确认')
      assertWorkflowMatchesExpected(confirmed, expectedWorkflow, '工作流创建结果待确认')
      selectedWorkflow.value = confirmed
      await loadRuns(confirmed.id)
      message.success('工作流已创建')
    }
    showEditor.value = false
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '保存失败'))
  }
}

async function handleDelete(id: number) {
  if (!ensureActiveProjectSelected('删除工作流')) {
    return
  }
  try {
    await workflowApi.remove(id)
    await loadWorkflows()
    if (findWorkflowById(id)) {
      throw new Error('工作流删除结果待确认')
    }
    message.success('工作流已删除')
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '删除失败'))
  }
}

async function cloneWorkflow(workflow: WorkflowRecord) {
  if (!ensureActiveProjectSelected('克隆工作流')) {
    return
  }
  try {
    const payload = {
      projectId: projectStore.activeProjectId,
      name: `${workflow.name} (副本)`,
      description: workflow.description || '',
      stepsJson: JSON.stringify(workflow.steps.map(step => sanitizeStep(step)))
    }
    const expectedWorkflow = {
      projectId: payload.projectId,
      name: payload.name,
      description: payload.description,
      steps: workflow.steps.map(step => sanitizeStep(step))
    }
    const res = await workflowApi.create(payload)
    const cloned = normalizeWorkflow(getResponseData(res, '工作流克隆结果待确认'))
    const confirmed = await reloadWorkflowById(cloned.id, '工作流克隆结果待确认')
    assertWorkflowMatchesExpected(confirmed, expectedWorkflow, '工作流克隆结果待确认')
    selectedWorkflow.value = confirmed
    await loadRuns(confirmed.id)
    message.success('工作流已克隆')
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '克隆失败'))
  }
}

async function handleExecute() {
  if (!selectedWorkflow.value?.id) return
  if (!ensureActiveProjectSelected('执行工作流')) {
    return
  }
  executing.value = true
  try {
    const res = await workflowApi.execute(selectedWorkflow.value.id)
    const run = normalizeRun(getResponseData(res, '工作流执行结果待确认'))
    await loadRuns(selectedWorkflow.value.id)
    const confirmed = runs.value?.find(item => item.id === run.id)
    if (!confirmed) {
      throw new Error('工作流执行结果待确认')
    }
    assertWorkflowRunMatchesExpected(confirmed, {
      workflowId: selectedWorkflow.value.id,
      projectId: projectStore.activeProjectId
    }, '工作流执行结果待确认')
    await loadAssetLibrary()
    message.success(confirmed.status === 'success' ? '工作流执行完成' : '工作流已开始执行')
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '执行失败'))
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
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed
      .filter((item: unknown): item is Record<string, unknown> => isPlainObject(item))
      .map((item) => ({
        ...item,
        stepIndex: typeof item.stepIndex === 'number' ? item.stepIndex : undefined,
        stepType: typeof item.stepType === 'string' ? item.stepType : undefined,
        status: typeof item.status === 'string' ? item.status : undefined
      }))
  } catch {
    return []
  }
}

function resolveRunStepCount(run: WorkflowRunVO) {
  const workflow = selectedWorkflow.value?.id === run.workflowId
    ? selectedWorkflow.value
    : findWorkflowById(run.workflowId)
  if (workflow?.steps.length) {
    return workflow.steps.length
  }
  const resultCount = parseRunResults(run).length
  return resultCount > 0 ? resultCount : undefined
}

function normalizeRunStepIndex(stepIndex: unknown, stepCount?: number) {
  if (typeof stepIndex !== 'number' || !Number.isFinite(stepIndex) || stepIndex < 0) {
    return undefined
  }
  if (typeof stepCount === 'number' && stepCount > 0) {
    return Math.min(stepIndex, stepCount - 1)
  }
  return stepIndex
}

function formatRunProgress(run: WorkflowRunVO) {
  const stepCount = resolveRunStepCount(run)
  const results = parseRunResults(run)
  const currentStep = normalizeRunStepIndex(run.currentStep, stepCount)
  if (run.status === 'success') {
    if (typeof stepCount === 'number' && stepCount > 0) {
      return `已完成 ${stepCount}/${stepCount} 步`
    }
    if (results.length > 0) {
      return `已完成 ${results.length} 步`
    }
    return '执行完成'
  }
  if (run.status === 'failed') {
    const failedStep = normalizeRunStepIndex(
      results.find(result => result.status === 'failed')?.stepIndex,
      stepCount
    ) ?? currentStep
    if (typeof failedStep === 'number' && typeof stepCount === 'number' && stepCount > 0) {
      return `失败于步骤 ${failedStep + 1}/${stepCount}`
    }
    if (typeof failedStep === 'number') {
      return `失败于步骤 ${failedStep + 1}`
    }
    return '执行失败'
  }
  if (run.status === 'running') {
    if (typeof currentStep === 'number' && typeof stepCount === 'number' && stepCount > 0) {
      return `正在执行第 ${currentStep + 1}/${stepCount} 步`
    }
    if (typeof currentStep === 'number') {
      return `正在执行第 ${currentStep + 1} 步`
    }
    return '执行中'
  }
  return runStatusLabel(run.status)
}

function formatRunResultStep(stepIndex: unknown) {
  return typeof stepIndex === 'number' ? `步骤 ${stepIndex + 1}` : '步骤待确认'
}

watch(() => projectStore.activeProjectId, async () => {
  selectedWorkflow.value = null
  runs.value = null
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

.scope-notice {
  margin: 8px 0 0;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(59, 130, 246, 0.2);
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.1), rgba(16, 185, 129, 0.08));
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
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

.loading-box {
  display: flex;
  justify-content: center;
  padding: 24px 0;
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

.editor-status-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
}

.editor-status {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
  font-size: 12px;
  line-height: 1.6;
}

.editor-status--error {
  background: rgba(239, 68, 68, 0.08);
  border-color: rgba(239, 68, 68, 0.18);
  color: #fca5a5;
}

.editor-status--info {
  background: rgba(148, 163, 184, 0.08);
  border-color: rgba(148, 163, 184, 0.16);
  color: #cbd5e1;
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
