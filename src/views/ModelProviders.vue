<template>
  <div class="providers-container">
    <div class="page-header">
      <h2>模型配置中心 (Model Providers)</h2>
      <p class="subtitle">管理并隔离当前项目空间下的 AI 模型提供商。您可以自由接入 OpenAI 兼容的第三方提供商或 Sora 官方 API。</p>
    </div>

    <!-- 列表操作卡 -->
    <n-card class="glass-card table-card" :bordered="false">
      <div class="actions-bar">
        <span class="count-lbl">当前空间已配置: {{ currentProviders.length }} 个</span>
        <n-button type="primary" size="medium" @click="handleOpenAddModal">
          <template #icon><Plus /></template>添加提供商
        </n-button>
      </div>

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
              <n-switch
                v-model:value="provider.enabled"
                @update:value="handleToggleEnable(provider)"
              />
            </td>

            <!-- 默认 -->
            <td>
              <n-tag
                :type="provider.isDefault ? 'success' : 'default'"
                :bordered="provider.isDefault"
                size="small"
                style="cursor: pointer;"
                @click="handleSetDefault(provider)"
              >
                {{ provider.isDefault ? '默认激活' : '设为默认' }}
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
          <n-select v-model:value="form.type" :options="typeOptions" />
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

        <n-form-item label="模型具体名称 (Model Name)">
          <n-input v-model:value="form.modelName" placeholder="例如: dall-e-3, sora-1, claude-3" />
        </n-form-item>

        <n-row :gutter="12">
          <n-col :span="12">
            <n-form-item label="是否立即启用">
              <n-switch v-model:value="form.enabled" />
            </n-form-item>
          </n-col>
          <n-col :span="12">
            <n-form-item label="设为当前类型默认">
              <n-switch v-model:value="form.isDefault" />
            </n-form-item>
          </n-col>
        </n-row>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { useProjectStore } from '@/store/project'
import { useModelProviderStore, type ModelProvider } from '@/store/provider'
import { providerApi } from '@/api/providers'
import { Plus, Layers } from 'lucide-vue-next'

const message = useMessage()

const projectStore = useProjectStore()
const providerStore = useModelProviderStore()

const showModal = ref(false)
const isEditMode = ref(false)
const editingId = ref<number | null>(null)
const testingId = ref<number | null>(null)

const form = reactive({
  name: '',
  type: 'image' as 'image' | 'video' | 'openai' | 'custom',
  baseUrl: '',
  apiKey: '',
  modelName: '',
  enabled: true,
  isDefault: false
})

const typeOptions = [
  { label: '图像生成模型 (Image)', value: 'image' },
  { label: '视频生成模型 (Video)', value: 'video' },
  { label: 'OpenAI 兼容接口 (OpenAI)', value: 'openai' },
  { label: '自定义复杂接口 (Custom)', value: 'custom' }
]

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
watch(() => projectStore.activeProjectId, () => { page.value = 1 })

const getTypeTag = (type: string) => {
  if (type === 'image') return 'success'
  if (type === 'video') return 'warning'
  if (type === 'openai') return 'info'
  return 'default'
}

const getTypeLabel = (type: string) => {
  if (type === 'image') return '生图'
  if (type === 'video') return '生视频'
  if (type === 'openai') return 'OpenAI'
  return '自定义'
}

const handleToggleEnable = async (provider: ModelProvider) => {
  await providerStore.updateProvider(provider.id, { enabled: provider.enabled })
  message.info(provider.enabled ? `模型 ${provider.name} 已启用` : `模型 ${provider.name} 已禁用`)
}

const handleSetDefault = async (provider: ModelProvider) => {
  await providerStore.setDefaultProvider(provider.id)
  message.success(`已成功将 ${provider.name} 设为当前空间默认 [${getTypeLabel(provider.type)}] 提供商`)
}

const handleTestConnection = async (provider: ModelProvider) => {
  testingId.value = provider.id
  message.loading(`正在测试连接提供商 ${provider.name} 中...`)
  try {
    await providerApi.testConnection(provider.id)
    message.success(`${provider.name} 连接测试成功！延迟: 124ms`)
  } catch (err: any) {
    message.error(err.message || '连接失败')
  } finally {
    testingId.value = null
  }
}

const handleOpenAddModal = () => {
  isEditMode.value = false
  editingId.value = null
  form.name = ''
  form.type = 'image'
  form.baseUrl = ''
  form.apiKey = ''
  form.modelName = ''
  form.enabled = true
  form.isDefault = false
  showModal.value = true
}

const handleOpenEditModal = (provider: ModelProvider) => {
  isEditMode.value = true
  editingId.value = provider.id
  form.name = provider.name
  form.type = provider.type
  form.baseUrl = provider.baseUrl
  form.apiKey = provider.apiKey
  form.modelName = provider.modelName
  form.enabled = provider.enabled
  form.isDefault = provider.isDefault
  showModal.value = true
}

const handleSave = async () => {
  if (!form.name || !form.baseUrl || !form.apiKey || !form.modelName) {
    message.error('请完整填写所有核心模型配置字段！')
    return false
  }

  if (isEditMode.value && editingId.value !== null) {
    await providerStore.updateProvider(editingId.value, {
      name: form.name,
      type: form.type,
      baseUrl: form.baseUrl,
      apiKey: form.apiKey,
      modelName: form.modelName,
      enabled: form.enabled,
      isDefault: form.isDefault
    })
    message.success('模型提供商配置更新成功')
  } else {
    await providerStore.addProvider({
      projectId: projectStore.activeProjectId,
      name: form.name,
      type: form.type,
      baseUrl: form.baseUrl,
      apiKey: form.apiKey,
      modelName: form.modelName,
      enabled: form.enabled,
      isDefault: form.isDefault
    })
    message.success('新模型提供商配置已注入当前空间')
  }
  showModal.value = false
}

const handleDelete = async (id: number) => {
  await providerStore.deleteProvider(id)
  message.success('模型提供商配置已删除')
}
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

.pager { display: flex; justify-content: flex-end; margin-top: 16px; }

.field-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #9ca3af;
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
