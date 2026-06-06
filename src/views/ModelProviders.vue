<template>
  <div class="providers-container">
    <div class="page-header">
      <h2>模型配置中心 (Model Providers)</h2>
      <p class="subtitle">管理并隔离当前项目空间下的 AI 模型提供商，支持图像、视频、字幕转写和真实 TTS 配音接口。</p>
    </div>

    <!-- 列表操作卡 -->
    <n-card class="glass-card table-card" :bordered="false">
      <div class="actions-bar">
        <span class="count-lbl">当前空间已配置: {{ currentProviders.length }} 个</span>
        <n-button type="primary" size="medium" :disabled="providerMetaLoadState !== 'ready'" @click="handleOpenAddModal">
          <template #icon><Plus /></template>添加提供商
        </n-button>
      </div>
      <div v-if="providerMetaLoadState === 'error'" class="status-note">提供商类型与音频格式待确认，请稍后重试。</div>
      <div v-if="providerListLoadState === 'error'" class="status-note">当前项目的提供商列表待确认，请稍后重试。</div>

      <n-table :single-line="false" class="providers-table" style="margin-top: 15px;">
        <thead>
          <tr>
            <th>提供商名称</th>
            <th>模型类型</th>
            <th>API Base URL</th>
            <th>映射模型名称 (Model)</th>
            <th style="width: 100px;">启用状态</th>
            <th style="width: 100px;">默认模型</th>
            <th style="width: 240px;">管理操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="provider in pagedProviders" :key="provider.id">
            <!-- 名称 -->
            <td>
              <div class="name-cell">
                <strong>{{ provider.name }}</strong>
                <span class="id-lbl">#{{ provider.id }}</span>
              </div>
            </td>

            <!-- 类型 -->
            <td>
              <n-tag :type="getTypeTag(provider.type)" size="small">
                {{ getTypeLabel(provider.type) }}
              </n-tag>
            </td>

            <!-- Base URL -->
            <td><code>{{ provider.baseUrl }}</code></td>

            <!-- 模型名称 -->
            <td><n-tag size="small" type="info"><code>{{ provider.modelName }}</code></n-tag></td>

            <!-- 启用 -->
            <td>
              <template v-if="provider.enabled === null">
                <n-tag size="small" type="warning">状态待确认</n-tag>
              </template>
              <n-switch
                v-else
                :value="provider.enabled"
                @update:value="(value: boolean) => handleToggleEnable(provider, value)"
              />
            </td>

            <!-- 默认 -->
            <td>
              <n-tag
                :type="providerDefaultTagType(provider)"
                :bordered="provider.isDefault === true"
                size="small"
                :style="{ cursor: canSetDefault(provider) ? 'pointer' : 'default' }"
                @click="canSetDefault(provider) ? handleSetDefault(provider) : undefined"
              >
                {{ providerDefaultLabel(provider) }}
              </n-tag>
            </td>

            <!-- 操作 -->
            <td>
              <n-space>
                <n-button
                  type="primary"
                  size="tiny"
                  secondary
                  :loading="testingId === provider.id"
                  @click="handleTestConnection(provider)"
                >
                  测试连接
                </n-button>
                <n-button type="warning" size="tiny" secondary @click="handleOpenEditModal(provider)">
                  编辑
                </n-button>
                <n-button type="error" size="tiny" tertiary @click="handleDelete(provider.id)">
                  删除
                </n-button>
              </n-space>
            </td>
          </tr>
          <tr v-if="currentProviders.length === 0">
            <td colspan="7" class="empty-row">
              <Layers class="empty-icon" />
              <span>当前空间未配置任何模型，点击右上角添加。</span>
            </td>
          </tr>
        </tbody>
      </n-table>
      <div class="pager" v-if="currentProviders.length > pageSize">
        <n-pagination v-model:page="page" :page-size="pageSize" :item-count="currentProviders.length" />
      </div>
    </n-card>

    <!-- 添加 / 编辑弹窗 -->
    <n-modal
      v-model:show="showModal"
      preset="dialog"
      :title="isEditMode ? '编辑模型提供商' : '新增模型提供商'"
      positive-text="保存配置"
      negative-text="取消"
      @positive-click="handleSave"
      style="width: 500px;"
    >
      <n-form :model="form" style="margin-top: 15px;" label-placement="top">
        <n-form-item label="提供商名称">
          <n-input v-model:value="form.name" placeholder="例如: 官方 OpenAI 接口, 备用 Sora 提供方" />
        </n-form-item>

        <n-form-item label="模型类型">
          <n-select
            v-model:value="form.type"
            :options="typeOptions"
            :disabled="providerMetaLoadState !== 'ready'"
            :placeholder="providerMetaLoadState === 'error' ? '模型类型待确认' : '选择模型类型'"
          />
        </n-form-item>

        <n-form-item label="API Base URL">
          <n-input v-model:value="form.baseUrl" placeholder="例如: https://api.openai.com/v1" />
          <div class="field-hint">填写 OpenAI 兼容接口地址，不要填写管理后台首页或文档站点地址。</div>
        </n-form-item>

        <n-form-item label="API Key">
          <n-input
            v-model:value="form.apiKey"
            type="password"
            show-password-on="mousedown"
            placeholder="请输入您的私有 API Key"
          />
        </n-form-item>

        <n-form-item :label="form.type === 'audio' ? '配音模型 (TTS Model)' : '模型具体名称 (Model Name)'">
          <n-input v-model:value="form.modelName" :placeholder="form.type === 'audio' ? '例如: tts-1, gpt-4o-mini-tts' : '例如: dall-e-3, sora-1, claude-3'" />
        </n-form-item>

        <template v-if="form.type === 'audio'">
          <n-form-item label="字幕转写模型 (可选)">
            <n-input v-model:value="form.transcriptionModel" placeholder="例如: whisper-1, gpt-4o-mini-transcribe" />
            <div class="field-hint">留空时默认复用上面的模型名；若配音和转写使用不同模型，建议单独填写。</div>
          </n-form-item>

          <n-row :gutter="12">
            <n-col :span="12">
              <n-form-item label="语音音色 (Voice)">
                <n-input v-model:value="form.voice" placeholder="例如: alloy, nova, shimmer" />
              </n-form-item>
            </n-col>
            <n-col :span="12">
              <n-form-item label="输出格式">
                <n-select
                  v-model:value="form.responseFormat"
                  :options="responseFormatOptions"
                  :disabled="providerMetaLoadState !== 'ready'"
                  :placeholder="providerMetaLoadState === 'error' ? '音频格式待确认' : '选择输出格式'"
                />
              </n-form-item>
            </n-col>
          </n-row>

          <n-row :gutter="12">
            <n-col :span="12">
              <n-form-item label="语速 (可选)">
                <n-input v-model:value="form.speed" placeholder="例如: 1 或 1.1" />
              </n-form-item>
            </n-col>
            <n-col :span="12">
              <div class="field-hint inline-hint">不填则按服务端默认语速生成。</div>
            </n-col>
          </n-row>

          <n-form-item label="朗读指令 (可选)">
            <n-input
              v-model:value="form.instructions"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 4 }"
              placeholder="例如: 用自然温和的中文女声朗读，语气平稳。"
            />
          </n-form-item>
        </template>

        <n-row :gutter="12">
          <n-col :span="12">
            <n-form-item label="是否立即启用">
              <n-switch :value="form.enabled" @update:value="handleEnabledChange" />
              <div v-if="preserveUnknownEnabled" class="field-hint inline-hint">当前启用状态未从服务端返回；若不手动切换，保存时会保持原值。</div>
            </n-form-item>
          </n-col>
          <n-col :span="12">
            <n-form-item label="设为当前类型默认">
              <n-switch :value="form.isDefault" @update:value="handleDefaultChange" />
              <div v-if="preserveUnknownDefault" class="field-hint inline-hint">当前默认状态未从服务端返回；若不手动切换，保存时会保持原值。</div>
            </n-form-item>
          </n-col>
        </n-row>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { useProjectStore } from '@/store/project'
import { useModelProviderStore, type ModelProvider } from '@/store/provider'
import { providerApi, type ProviderMetaOption, type ProviderMetaVO } from '@/api/providers'
import { Plus, Layers } from 'lucide-vue-next'

const message = useMessage()

const projectStore = useProjectStore()
const providerStore = useModelProviderStore()

const showModal = ref(false)
const isEditMode = ref(false)
const editingId = ref<number | null>(null)
const testingId = ref<number | null>(null)
const providerMeta = ref<ProviderMetaVO>(emptyProviderMeta())
const providerMetaLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const providerListLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const preserveUnknownEnabled = ref(false)
const preserveUnknownDefault = ref(false)
const enabledTouched = ref(false)
const defaultTouched = ref(false)

const form = reactive({
  name: '',
  type: '' as string,
  baseUrl: '',
  apiKey: '',
  modelName: '',
  enabled: true,
  isDefault: false,
  configJson: '',
  transcriptionModel: '',
  voice: '',
  responseFormat: '',
  speed: '',
  instructions: ''
})

const typeOptions = computed(() => providerMeta.value.providerTypes || [])
const responseFormatOptions = computed(() => providerMeta.value.audioResponseFormats || [])
const defaultProviderType = computed(() => providerMeta.value.defaults?.providerType || typeOptions.value[0]?.value || '')
const defaultResponseFormat = computed(() => providerMeta.value.defaults?.audioResponseFormat || responseFormatOptions.value[0]?.value || '')

const currentProviders = computed(() => {
  return providerStore.getProvidersByProject(projectStore.activeProjectId)
})

// 前端分页(providerStore 全量不动,仅渲染层切片)
const page = ref(1)
const pageSize = 10
const pagedProviders = computed(() => {
  const start = (page.value - 1) * pageSize
  return currentProviders.value.slice(start, start + pageSize)
})
watch(() => projectStore.activeProjectId, () => {
  page.value = 1
  void loadProviderList()
})

function emptyProviderMeta(): ProviderMetaVO {
  return {
    providerTypes: [],
    audioResponseFormats: [],
    defaults: {}
  }
}

function getTypeMeta(type: string) {
  return typeOptions.value.find(option => option.value === type)
}

const getTypeTag = (type: string) => {
  return getTypeMeta(type)?.tagType || 'default'
}

const getTypeLabel = (type: string) => {
  return getTypeMeta(type)?.shortLabel || type || '未知'
}

function resolveOptionValue(options: ProviderMetaOption[], preferredValue?: string, fallbackValue = '') {
  if (preferredValue && options.length === 0) {
    return preferredValue
  }
  if (preferredValue && options.some(option => option.value === preferredValue)) {
    return preferredValue
  }
  return options[0]?.value || fallbackValue
}

function requireSavedProvider(provider: ModelProvider, action: 'create' | 'update') {
  const hasValidId = Number.isFinite(provider.id) && provider.id > 0
  if (!hasValidId || !provider.name || !provider.type || !provider.baseUrl || !provider.modelName) {
    throw new Error(action === 'create' ? '模型提供商创建结果待确认' : '模型提供商更新结果待确认')
  }
  return provider
}

function requireEnabledState(provider: ModelProvider, enabled: boolean) {
  if (provider.enabled !== enabled) {
    throw new Error(enabled ? '模型启用状态待确认' : '模型禁用状态待确认')
  }
  return provider
}

function requireDefaultState(provider: ModelProvider, expectedType?: string) {
  if (provider.isDefault !== true) {
    throw new Error('默认提供商状态待确认')
  }
  if (expectedType && provider.type !== expectedType) {
    throw new Error('默认提供商类型待确认')
  }
  return provider
}

function requireConnectionTestResult(result: boolean) {
  if (result !== true) {
    throw new Error('连接测试结果待确认')
  }
}

function requireDeletedProvider(id: number) {
  if (providerStore.providers.some(item => item.id === id)) {
    throw new Error('模型提供商删除结果待确认')
  }
}

function requireSavedProviderState(
  provider: ModelProvider,
  expected: {
    enabled?: boolean
    isDefault?: boolean
  }
) {
  if (typeof expected.enabled === 'boolean' && provider.enabled !== expected.enabled) {
    throw new Error('模型提供商启用状态待确认')
  }
  if (typeof expected.isDefault === 'boolean' && provider.isDefault !== expected.isDefault) {
    throw new Error(expected.isDefault ? '默认提供商状态待确认' : '模型提供商默认状态待确认')
  }
  return provider
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

function requireProviderMetaOptionList(value: unknown, errorMessage: string) {
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
    return {
      label,
      value: optionValue,
      shortLabel: typeof item.shortLabel === 'string' ? item.shortLabel.trim() : undefined,
      tagType: typeof item.tagType === 'string' ? item.tagType.trim() : undefined
    }
  })
}

function requireProviderMeta(value: unknown): ProviderMetaVO {
  const errorMessage = '提供商元数据待确认'
  if (!isPlainObject(value)) {
    throw new Error(errorMessage)
  }
  const defaults = value.defaults
  if (defaults != null && !isPlainObject(defaults)) {
    throw new Error(errorMessage)
  }
  return {
    providerTypes: requireProviderMetaOptionList(value.providerTypes, errorMessage),
    audioResponseFormats: requireProviderMetaOptionList(value.audioResponseFormats, errorMessage),
    defaults: defaults
      ? {
          providerType: typeof defaults.providerType === 'string' ? defaults.providerType.trim() : undefined,
          audioResponseFormat: typeof defaults.audioResponseFormat === 'string' ? defaults.audioResponseFormat.trim() : undefined
        }
      : {}
  }
}

async function loadProviderMeta() {
  providerMetaLoadState.value = 'loading'
  try {
    const res = await providerApi.getMeta()
    providerMeta.value = requireProviderMeta((res as any).data)
    providerMetaLoadState.value = 'ready'
  } catch {
    providerMeta.value = emptyProviderMeta()
    providerMetaLoadState.value = 'error'
  }
}

async function loadProviderList() {
  providerListLoadState.value = 'loading'
  try {
    if (!projectStore.activeProjectId) {
      providerStore.clear()
      providerListLoadState.value = 'ready'
      return
    }
    await providerStore.refresh(projectStore.activeProjectId)
    const hasForeignProjectProvider = currentProviders.value.some(provider =>
      provider.projectId !== projectStore.activeProjectId && provider.projectId !== 0
    )
    if (hasForeignProjectProvider) {
      throw new Error('模型提供商数据待确认')
    }
    providerListLoadState.value = 'ready'
  } catch (err: any) {
    providerListLoadState.value = 'error'
    message.error(err.message || '加载模型提供商失败')
  }
}

const handleToggleEnable = async (provider: ModelProvider, enabled: boolean) => {
  requireEnabledState(await providerStore.updateProvider(provider.id, { enabled }), enabled)
  message.info(enabled ? `模型 ${provider.name} 已启用` : `模型 ${provider.name} 已禁用`)
}

const handleSetDefault = async (provider: ModelProvider) => {
  await providerStore.setDefaultProvider(provider.id)
  requireDefaultState(
    providerStore.providers.find(item => item.id === provider.id) || provider,
    provider.type
  )
  message.success(`已成功将 ${provider.name} 设为当前空间默认 [${getTypeLabel(provider.type)}] 提供商`)
}

const providerDefaultLabel = (provider: ModelProvider) => {
  if (provider.isDefault === null) return '默认状态待确认'
  return provider.isDefault ? '默认激活' : '设为默认'
}

const providerDefaultTagType = (provider: ModelProvider) => {
  if (provider.isDefault === null) return 'warning'
  return provider.isDefault ? 'success' : 'default'
}

const canSetDefault = (provider: ModelProvider) => provider.isDefault === false

const handleEnabledChange = (value: boolean) => {
  form.enabled = value
  enabledTouched.value = true
  preserveUnknownEnabled.value = false
}

const handleDefaultChange = (value: boolean) => {
  form.isDefault = value
  defaultTouched.value = true
  preserveUnknownDefault.value = false
}

const handleTestConnection = async (provider: ModelProvider) => {
  testingId.value = provider.id
  message.loading(`正在测试连接提供商 ${provider.name} 中...`)
  try {
    requireConnectionTestResult(await providerStore.testConnection(provider.id))
    message.success(`${provider.name} 连接测试成功`)
  } catch (err: any) {
    message.error(err.message || '连接失败')
  } finally {
    testingId.value = null
  }
}

const handleOpenAddModal = () => {
  if (typeOptions.value.length === 0) {
    message.error('模型类型元数据尚未加载成功，暂时无法基于真实配置新增提供商')
    return
  }
  isEditMode.value = false
  editingId.value = null
  form.name = ''
  form.type = defaultProviderType.value
  form.baseUrl = ''
  form.apiKey = ''
  form.modelName = ''
  form.enabled = true
  form.isDefault = false
  form.configJson = ''
  form.transcriptionModel = ''
  form.voice = ''
  form.responseFormat = defaultResponseFormat.value
  form.speed = ''
  form.instructions = ''
  preserveUnknownEnabled.value = false
  preserveUnknownDefault.value = false
  enabledTouched.value = false
  defaultTouched.value = false
  showModal.value = true
}

const handleOpenEditModal = (provider: ModelProvider) => {
  const config = parseConfigJson(provider.configJson)
  isEditMode.value = true
  editingId.value = provider.id
  form.name = provider.name
  form.type = provider.type
  form.baseUrl = provider.baseUrl
  form.apiKey = provider.apiKey
  form.modelName = provider.modelName
  form.enabled = provider.enabled === true
  form.isDefault = provider.isDefault === true
  form.configJson = provider.configJson || ''
  form.transcriptionModel = config.transcriptionModel || ''
  form.voice = config.voice || ''
  form.responseFormat = resolveOptionValue(responseFormatOptions.value, config.responseFormat, defaultResponseFormat.value)
  form.speed = config.speed ? String(config.speed) : ''
  form.instructions = config.instructions || ''
  preserveUnknownEnabled.value = provider.enabled === null
  preserveUnknownDefault.value = provider.isDefault === null
  enabledTouched.value = false
  defaultTouched.value = false
  showModal.value = true
}

const handleSave = async () => {
  if (!form.type) {
    message.error('请选择真实的模型类型后再保存')
    return false
  }
  if (!form.name || !form.baseUrl || !form.apiKey || !form.modelName) {
    message.error('请完整填写所有核心模型配置字段！')
    return false
  }

  const configJson = buildConfigJson()

  if (isEditMode.value && editingId.value !== null) {
    const payload: Partial<ModelProvider> = {
      name: form.name,
      type: form.type,
      baseUrl: form.baseUrl,
      apiKey: form.apiKey,
      modelName: form.modelName,
      configJson
    }
    if (!preserveUnknownEnabled.value || enabledTouched.value) {
      payload.enabled = form.enabled
    }
    if (!preserveUnknownDefault.value || defaultTouched.value) {
      payload.isDefault = form.isDefault
    }
    const expectedEnabled = typeof payload.enabled === 'boolean' ? payload.enabled : undefined
    const expectedDefault = typeof payload.isDefault === 'boolean' ? payload.isDefault : undefined
    requireSavedProviderState(
      requireSavedProvider(await providerStore.updateProvider(editingId.value, payload), 'update'),
      {
        enabled: expectedEnabled,
        isDefault: expectedDefault
      }
    )
    message.success('模型提供商配置更新成功')
  } else {
    requireSavedProviderState(
      requireSavedProvider(await providerStore.addProvider({
        projectId: projectStore.activeProjectId,
        name: form.name,
        type: form.type,
        baseUrl: form.baseUrl,
        apiKey: form.apiKey,
        modelName: form.modelName,
        enabled: form.enabled,
        isDefault: form.isDefault,
        configJson
      }), 'create'),
      {
        enabled: form.enabled,
        isDefault: form.isDefault
      }
    )
    message.success('新模型提供商配置已注入当前空间')
  }
  showModal.value = false
}

const handleDelete = async (id: number) => {
  await providerStore.deleteProvider(id)
  requireDeletedProvider(id)
  message.success('模型提供商配置已删除')
}

const parseConfigJson = (value?: string) => {
  if (!value) {
    return {} as Record<string, any>
  }
  try {
    return JSON.parse(value)
  } catch {
    return {} as Record<string, any>
  }
}

const buildConfigJson = () => {
  if (form.type !== 'audio') {
    return form.configJson || ''
  }
  const payload: Record<string, any> = {}
  if (form.transcriptionModel.trim()) payload.transcriptionModel = form.transcriptionModel.trim()
  if (form.voice.trim()) payload.voice = form.voice.trim()
  if (form.responseFormat.trim()) payload.responseFormat = form.responseFormat.trim().toLowerCase()
  if (form.speed.trim()) {
    const speedValue = Number(form.speed)
    if (!Number.isNaN(speedValue) && speedValue > 0) {
      payload.speed = speedValue
    }
  }
  if (form.instructions.trim()) payload.instructions = form.instructions.trim()
  return Object.keys(payload).length ? JSON.stringify(payload) : ''
}

onMounted(async () => {
  await Promise.allSettled([loadProviderMeta(), loadProviderList()])
})
</script>

<style scoped>
.providers-container {
  padding-bottom: 40px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 6px 0;
  color: #fff;
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

.actions-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.count-lbl {
  font-size: 13px;
  color: #9ca3af;
}
.status-note {
  margin-bottom: 12px;
  font-size: 12px;
  color: #fca5a5;
}

.pager { display: flex; justify-content: flex-end; margin-top: 16px; }

.field-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #9ca3af;
}

.inline-hint {
  padding-top: 36px;
}

/* 表格样式 */
.providers-table {
  background-color: transparent !important;
}

.providers-table th {
  background-color: rgba(255, 255, 255, 0.02) !important;
  color: #9ca3af !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06) !important;
  font-size: 13px;
}

.providers-table td {
  border-bottom: 1px solid rgba(255, 255, 255, 0.04) !important;
  color: #e5e7eb;
  padding: 16px 12px;
}

.name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.id-lbl {
  font-size: 10px;
  color: #6b7280;
}

.empty-row {
  text-align: center !important;
  padding: 60px 0 !important;
  color: #6b7280;
}

.empty-row .empty-icon {
  width: 44px;
  height: 44px;
  margin: 0 auto 12px auto;
  opacity: 0.3;
}
</style>
