<template>
  <div class="stylecards-container">
    <div class="page-header">
      <h2>角色卡 / 风格卡 (Character & Style Cards)</h2>
      <p class="subtitle">保存可复用的角色设定或风格预设，在生图与视频页一键应用。</p>
    </div>

    <!-- 过滤工具栏 -->
    <n-card class="glass-card filter-card" :bordered="false">
      <div class="filter-row">
        <n-tabs v-model:value="activeType" type="segment" class="filter-tabs">
          <n-tab name="all">全部</n-tab>
          <n-tab name="style">风格预设</n-tab>
          <n-tab name="character">角色卡片</n-tab>
        </n-tabs>
        <n-space>
          <n-input v-model:value="searchQuery" clearable size="small" placeholder="搜索卡片..." style="width:160px;">
            <template #prefix><Search class="inline-icon" /></template>
          </n-input>
          <n-button type="primary" size="medium" @click="showAddModal = true; editingCard = null; resetForm()">
            <template #icon><Plus /></template>新建卡片
          </n-button>
        </n-space>
      </div>
    </n-card>

    <!-- 卡片网格 -->
    <div class="cards-grid" v-if="filteredCards.length > 0">
      <div v-for="card in filteredCards" :key="card.id" class="card-item glass-card">
        <div class="card-preview" v-if="card.previewUrl">
          <img :src="card.previewUrl" class="preview-img" />
        </div>
        <div class="card-preview placeholder-preview" v-else>
          <div class="placeholder-icon-box" :class="card.type">
            <User v-if="card.type === 'character'" class="placeholder-icon" />
            <Palette v-else class="placeholder-icon" />
          </div>
        </div>

        <div class="card-body">
          <div class="card-head">
            <span class="card-name">{{ card.name }}</span>
            <n-tag :type="card.type === 'character' ? 'info' : 'success'" size="tiny" round>
              {{ card.type === 'character' ? '角色' : '风格' }}
            </n-tag>
          </div>
          <p class="card-desc">{{ card.content.substring(0, 80) }}{{ card.content.length > 80 ? '...' : '' }}</p>
          <div class="card-tags" v-if="card.tag">
            <n-tag size="mini" type="warning" round>{{ card.tag }}</n-tag>
          </div>
          <div class="card-meta" v-if="card.modelName">
            <code>{{ card.modelName }}</code>
          </div>
        </div>

        <div class="card-actions">
          <n-button type="primary" size="tiny" secondary @click="handleApply(card)">
            <template #icon><Zap /></template>应用
          </n-button>
          <n-button size="tiny" secondary @click="handleEdit(card)">
            <template #icon><Edit3 /></template>编辑
          </n-button>
          <n-button type="error" size="tiny" tertiary @click="handleDelete(card.id)">
            <template #icon><Trash2 /></template>
          </n-button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div class="empty-box" v-else>
      <Palette class="empty-icon" />
      <h3>暂无角色卡或风格卡</h3>
      <p>创建一个预设卡片，保存完整的提示词、模型和参数配置，下次一键复用。</p>
    </div>

    <!-- 新建/编辑弹窗 -->
    <n-modal v-model:show="showAddModal" preset="card" title="新建卡片" style="width: 540px;" closable>
      <n-form :model="form" label-placement="top" style="margin-top: 10px;">
        <n-row :gutter="16">
          <n-col :span="12">
            <n-form-item label="卡片名称">
              <n-input v-model:value="form.name" placeholder="例如：赛博朋克女主" />
            </n-form-item>
          </n-col>
          <n-col :span="12">
            <n-form-item label="类型">
              <n-select v-model:value="form.type" :options="[{ label: '风格预设', value: 'style' }, { label: '角色卡片', value: 'character' }]" />
            </n-form-item>
          </n-col>
        </n-row>

        <n-form-item label="提示词内容 (Prompt)">
          <n-input v-model:value="form.content" type="textarea" :autosize="{ minRows: 3, maxRows: 5 }" placeholder="输入完整的提示词描述..." />
        </n-form-item>

        <n-form-item label="排除要素 (Negative Prompt)">
          <n-input v-model:value="form.negativePrompt" type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" placeholder="可选" />
        </n-form-item>

        <n-row :gutter="16">
          <n-col :span="12">
            <n-form-item label="模型名称">
              <n-input v-model:value="form.modelName" placeholder="例如：dall-e-3" />
            </n-form-item>
          </n-col>
          <n-col :span="12">
            <n-form-item label="分类标签">
              <n-input v-model:value="form.tag" placeholder="例如：赛博朋克" />
            </n-form-item>
          </n-col>
        </n-row>

        <n-row :gutter="16">
          <n-col :span="8">
            <n-form-item label="CFG">
              <n-input-number v-model:value="form.cfg" :min="1" :max="20" :step="0.5" placeholder="7.5" />
            </n-form-item>
          </n-col>
          <n-col :span="8">
            <n-form-item label="Steps">
              <n-input-number v-model:value="form.steps" :min="10" :max="50" :step="1" placeholder="25" />
            </n-form-item>
          </n-col>
          <n-col :span="8">
            <n-form-item label="尺寸">
              <n-input v-model:value="form.size" placeholder="1024x1024" />
            </n-form-item>
          </n-col>
        </n-row>

        <n-form-item label="预览图 URL">
          <n-input v-model:value="form.previewUrl" placeholder="可选：输入示例图的 URL" />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button @click="showAddModal = false">取消</n-button>
          <n-button type="primary" @click="handleSave">{{ editingCard ? '更新' : '创建' }}</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useProjectStore } from '@/store/project'
import { styleCardApi, type StyleCard } from '@/api/styleCards'
import { Plus, Zap, Edit3, Trash2, User, Palette, Search } from 'lucide-vue-next'

const router = useRouter()
const message = useMessage()
const projectStore = useProjectStore()

const activeType = ref('all')
const searchQuery = ref('')
const cards = ref<StyleCard[]>([])
const showAddModal = ref(false)
const editingCard = ref<StyleCard | null>(null)

const form = reactive({
  name: '',
  type: 'style' as 'character' | 'style',
  content: '',
  negativePrompt: '',
  modelName: '',
  tag: '',
  cfg: null as number | null,
  steps: null as number | null,
  size: '',
  previewUrl: ''
})

const filteredCards = computed(() => {
  let list = cards.value.filter(c => c.projectId === projectStore.activeProjectId || !c.projectId)
  if (activeType.value !== 'all') {
    list = list.filter(c => c.type === activeType.value)
  }
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter(c => c.name?.toLowerCase().includes(q) || c.content?.toLowerCase().includes(q))
  }
  return list
})

async function loadCards() {
  try {
    const res = await styleCardApi.list(projectStore.activeProjectId)
    cards.value = (res.data || []).map((c: any) => ({
      ...c,
      id: Number(c.id),
      projectId: Number(c.projectId)
    }))
  } catch {
    cards.value = []
  }
}

onMounted(loadCards)

function resetForm() {
  form.name = ''
  form.type = 'style'
  form.content = ''
  form.negativePrompt = ''
  form.modelName = ''
  form.tag = ''
  form.cfg = null
  form.steps = null
  form.size = ''
  form.previewUrl = ''
  editingCard.value = null
}

function handleEdit(card: StyleCard) {
  editingCard.value = card
  form.name = card.name
  form.type = card.type
  form.content = card.content
  form.negativePrompt = card.negativePrompt || ''
  form.modelName = card.modelName || ''
  form.tag = card.tag || ''
  form.cfg = card.cfg ?? null
  form.steps = card.steps ?? null
  form.size = card.size || ''
  form.previewUrl = card.previewUrl || ''
  showAddModal.value = true
}

async function handleSave() {
  if (!form.name || !form.content) {
    message.error('名称和提示词不能为空')
    return
  }

  const payload = {
    projectId: projectStore.activeProjectId,
    name: form.name,
    type: form.type,
    content: form.content,
    negativePrompt: form.negativePrompt || undefined,
    modelName: form.modelName || undefined,
    cfg: form.cfg ?? undefined,
    steps: form.steps ?? undefined,
    size: form.size || undefined,
    tag: form.tag || undefined,
    previewUrl: form.previewUrl || undefined
  }

  try {
    if (editingCard.value) {
      await styleCardApi.update(editingCard.value.id, payload)
      message.success('卡片已更新')
    } else {
      await styleCardApi.create(payload as any)
      message.success('卡片已创建')
    }
    showAddModal.value = false
    resetForm()
    await loadCards()
  } catch (err: any) {
    message.error(err.message || '操作失败')
  }
}

async function handleDelete(id: number) {
  try {
    await styleCardApi.delete(id)
    cards.value = cards.value.filter(c => c.id !== id)
    message.success('已删除')
  } catch (err: any) {
    message.error(err.message || '删除失败')
  }
}

function handleApply(card: StyleCard) {
  const baseParams: Record<string, string> = {
    prompt: card.content,
    model: card.modelName || ''
  }
  if (card.negativePrompt) baseParams.negPrompt = card.negativePrompt
  if (card.cfg) baseParams.cfg = String(card.cfg)
  if (card.steps) baseParams.steps = String(card.steps)
  if (card.size) baseParams.size = card.size

  router.push({
    path: '/generate/image',
    query: baseParams
  })
  message.success(`已应用「${card.name}」，跳转至生图面板`)
}
</script>

<style scoped>
.stylecards-container {
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

.filter-card {
  margin-bottom: 24px;
}

.filter-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-tabs {
  max-width: 400px;
}

/* 卡片网格 */
.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.card-item {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
}

.card-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.3);
}

.card-preview {
  height: 120px;
  overflow: hidden;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.placeholder-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.02);
}

.placeholder-icon-box {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-icon-box.character {
  background: rgba(59, 130, 246, 0.15);
}

.placeholder-icon-box.style {
  background: rgba(16, 185, 129, 0.15);
}

.placeholder-icon {
  width: 24px;
  height: 24px;
  color: #9ca3af;
}

.card-body {
  padding: 14px 16px 10px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-name {
  font-size: 14px;
  font-weight: 600;
  color: #f3f4f6;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.4;
  margin: 0;
}

.card-tags {
  display: flex;
  gap: 4px;
}

.card-meta code {
  font-size: 10px;
  color: #6b7280;
}

.card-actions {
  display: flex;
  gap: 6px;
  padding: 8px 16px 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}

.empty-box {
  text-align: center;
  padding: 80px 20px;
  color: #6b7280;
}

.empty-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 16px;
  opacity: 0.3;
}

.empty-box h3 {
  font-size: 18px;
  color: #9ca3af;
  margin: 0 0 8px;
}

.empty-box p {
  font-size: 13px;
  color: #6b7280;
  max-width: 400px;
  margin: 0 auto;
}
</style>
