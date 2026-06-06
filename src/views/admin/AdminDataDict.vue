<template>
  <div class="admin-dict">
    <div class="page-header">
      <h2>数据字典 (Data Dictionary)</h2>
      <p class="subtitle">管理系统分类标签、枚举值和固定选项。</p>
    </div>

    <n-row :gutter="16">
      <!-- 左侧字典列表 -->
      <n-col :span="8">
        <n-card class="glass-card" :bordered="false">
          <template #header>
            <div class="card-header">
              <span>字典列表 ({{ dictCountDisplay }})</span>
              <n-button size="tiny" primary @click="showDictEditor = true; editingDict = null; Object.assign(dictForm, { code: '', name: '', description: '' })">
                <template #icon><Plus /></template>
              </n-button>
            </div>
          </template>
          <div v-if="dictsLoadState === 'error'" class="status-note">字典列表待确认，请稍后重试。</div>
          <div v-for="d in dicts || []" :key="d.id" class="dict-item" :class="{ active: activeDict?.id === d.id }" @click="selectDict(d)">
            <div class="dict-info">
              <span class="dict-name">{{ d.dictName }}</span>
              <span class="dict-code"><code>{{ d.dictCode }}</code></span>
            </div>
            <n-button size="tiny" text type="error" @click.stop="handleDeleteDict(d.id)">×</n-button>
          </div>
          <n-empty v-if="dicts !== null && dicts.length === 0" description="暂无字典" style="padding:20px" />
          <n-empty v-else-if="dicts === null" description="字典数据待确认，请稍后重试。" style="padding:20px" />
        </n-card>
      </n-col>

      <!-- 右侧条目列表 -->
      <n-col :span="16">
        <n-card class="glass-card" :bordered="false" v-if="activeDict">
          <template #header>
            <div class="card-header">
              <span>{{ activeDict.dictName }} — 条目 ({{ itemCountDisplay }})</span>
              <n-space>
                <n-button size="tiny" @click="showDictEditor = true; editingDict = activeDict.id; Object.assign(dictForm, { code: activeDict.dictCode, name: activeDict.dictName, description: activeDict.description || '' })">编辑字典</n-button>
                <n-button size="tiny" primary @click="showItemEditor = true; editingItem = null; Object.assign(itemForm, { code: '', name: '', sortOrder: 0 })">添加条目</n-button>
              </n-space>
            </div>
          </template>
          <div v-if="itemsLoadState === 'error'" class="status-note">字典条目待确认，请稍后重试。</div>

          <n-table :single-line="false" class="dict-table">
            <thead>
              <tr><th style="width:80px">排序</th><th>编码</th><th>名称</th><th style="width:80px">状态</th><th style="width:140px">操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in items || []" :key="item.id">
                <td>{{ item.sortOrder }}</td>
                <td><code>{{ item.itemCode }}</code></td>
                <td>{{ item.itemName }}</td>
                <td>
                  <n-tag v-if="normalizeBinaryStatus(item.status) === null" size="small" type="warning">状态待确认</n-tag>
                  <n-switch v-else :value="normalizeBinaryStatus(item.status) === true" @update:value="toggleItemStatus(item)" size="small" />
                </td>
                <td>
                  <n-space>
                    <n-button size="tiny" secondary @click="editItem(item)">编辑</n-button>
                    <n-button size="tiny" type="error" tertiary @click="handleDeleteItem(item.id)">删除</n-button>
                  </n-space>
                </td>
              </tr>
              <tr v-if="items !== null && items.length === 0">
                <td colspan="5" class="empty-cell">当前字典暂无条目</td>
              </tr>
              <tr v-else-if="items === null">
                <td colspan="5" class="empty-cell">字典条目待确认，请稍后重试。</td>
              </tr>
            </tbody>
          </n-table>
        </n-card>
        <n-card class="glass-card" :bordered="false" v-else>
          <n-empty description="请从左侧选择一个字典" style="padding:40px" />
        </n-card>
      </n-col>
    </n-row>

    <!-- 字典编辑弹窗 -->
    <n-modal v-model:show="showDictEditor" preset="dialog" :title="editingDict ? '编辑字典' : '新建字典'" positive-text="保存" negative-text="取消" @positive-click="handleSaveDict">
      <n-form :model="dictForm" style="margin-top:10px;">
        <n-form-item label="编码">
          <n-input v-model:value="dictForm.code" :disabled="!!editingDict" placeholder="例如：asset_category" />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="dictForm.name" placeholder="例如：资产分类" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="dictForm.description" placeholder="可选" />
        </n-form-item>
      </n-form>
    </n-modal>

    <!-- 条目编辑弹窗 -->
    <n-modal v-model:show="showItemEditor" preset="dialog" :title="editingItem ? '编辑条目' : '添加条目'" positive-text="保存" negative-text="取消" @positive-click="handleSaveItem">
      <n-form :model="itemForm" style="margin-top:10px;">
        <n-form-item label="编码">
          <n-input v-model:value="itemForm.code" :disabled="!!editingItem" placeholder="例如：image" />
        </n-form-item>
        <n-form-item label="名称">
          <n-input v-model:value="itemForm.name" placeholder="例如：图片" />
        </n-form-item>
        <n-form-item label="排序">
          <n-input-number v-model:value="itemForm.sortOrder" :min="0" :max="999" />
        </n-form-item>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import request from '@/api/request'

interface DictRecord {
  id: number
  dictCode: string
  dictName: string
  description: string
  status: boolean | null
  createdAt: string
  updatedAt: string
}

interface DictItemRecord {
  id: number
  dictId: number
  itemCode: string
  itemName: string
  sortOrder: number
  status: boolean | null
  createdAt: string
  updatedAt: string
}

const message = useMessage()
const dicts = ref<DictRecord[] | null>(null)
const activeDict = ref<DictRecord | null>(null)
const items = ref<DictItemRecord[] | null>(null)
const showDictEditor = ref(false)
const editingDict = ref<number | null>(null)
const dictForm = reactive({ code: '', name: '', description: '' })
const showItemEditor = ref(false)
const editingItem = ref<number | null>(null)
const itemForm = reactive({ code: '', name: '', sortOrder: 0 })
const dictsLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const itemsLoadState = ref<'loading' | 'ready' | 'error'>('ready')
const dictCountDisplay = computed(() => dicts.value === null ? '-' : dicts.value.length)
const itemCountDisplay = computed(() => items.value === null ? '-' : items.value.length)
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string) {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function normalizeOptionalText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function requireDict(value: unknown, action: 'create' | 'update') {
  if (!isPlainObject(value)) {
    throw new Error(action === 'create' ? '字典创建结果待确认' : '字典更新结果待确认')
  }
  const id = Number(value.id)
  const code = normalizeOptionalText(value.dictCode)
  const name = normalizeOptionalText(value.dictName)
  if (!Number.isFinite(id) || id <= 0 || !code || !name) {
    throw new Error(action === 'create' ? '字典创建结果待确认' : '字典更新结果待确认')
  }
  return {
    id,
    code,
    name,
    description: typeof value.description === 'string' ? value.description : '',
    status: normalizeBinaryStatus(value.status),
    createdAt: typeof value.createdAt === 'string' ? value.createdAt : '',
    updatedAt: typeof value.updatedAt === 'string' ? value.updatedAt : ''
  }
}

function requireDictItem(value: unknown, action: 'create' | 'update') {
  if (!isPlainObject(value)) {
    throw new Error(action === 'create' ? '条目创建结果待确认' : '条目更新结果待确认')
  }
  const id = Number(value.id)
  const code = normalizeOptionalText(value.itemCode)
  const name = normalizeOptionalText(value.itemName)
  if (!Number.isFinite(id) || id <= 0 || !code || !name) {
    throw new Error(action === 'create' ? '条目创建结果待确认' : '条目更新结果待确认')
  }
  return {
    id,
    dictId: Number(value.dictId),
    code,
    name,
    sortOrder: Number(value.sortOrder),
    status: normalizeBinaryStatus(value.status),
    createdAt: typeof value.createdAt === 'string' ? value.createdAt : '',
    updatedAt: typeof value.updatedAt === 'string' ? value.updatedAt : ''
  }
}

function normalizeDictRecord(value: unknown): DictRecord {
  const dict = requireDict(value, 'update')
  return {
    id: dict.id,
    dictCode: dict.code,
    dictName: dict.name,
    description: dict.description,
    status: dict.status,
    createdAt: dict.createdAt,
    updatedAt: dict.updatedAt
  }
}

function normalizeDictList(value: unknown): DictRecord[] {
  if (!Array.isArray(value)) {
    throw new Error('字典数据待确认')
  }
  const normalized = value.map((item: unknown) => normalizeDictRecord(item))
  const ids = new Set<number>()
  const codes = new Set<string>()
  for (const item of normalized) {
    if (ids.has(item.id) || codes.has(item.dictCode)) {
      throw new Error('字典数据待确认')
    }
    ids.add(item.id)
    codes.add(item.dictCode)
  }
  return normalized
}

function normalizeDictItemRecord(value: unknown): DictItemRecord {
  const item = requireDictItem(value, 'update')
  if (!Number.isFinite(item.dictId) || item.dictId <= 0 || !Number.isFinite(item.sortOrder) || item.sortOrder < 0) {
    throw new Error('字典条目待确认')
  }
  return {
    id: item.id,
    dictId: item.dictId,
    itemCode: item.code,
    itemName: item.name,
    sortOrder: item.sortOrder,
    status: item.status,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt
  }
}

function normalizeDictItemList(value: unknown): DictItemRecord[] {
  if (!Array.isArray(value)) {
    throw new Error('字典条目待确认')
  }
  const normalized = value.map((item: unknown) => normalizeDictItemRecord(item))
  const ids = new Set<number>()
  const codes = new Set<string>()
  for (const item of normalized) {
    if (ids.has(item.id) || codes.has(item.itemCode)) {
      throw new Error('字典条目待确认')
    }
    ids.add(item.id)
    codes.add(item.itemCode)
  }
  return normalized
}

function syncActiveDictFromList() {
  if (!activeDict.value || !dicts.value) {
    return
  }
  activeDict.value = dicts.value.find(item => Number(item.id) === Number(activeDict.value?.id)) || null
}

onMounted(loadDicts)

async function loadDicts() {
  dictsLoadState.value = 'loading'
  try {
    const response = await request.get<unknown>('/api/admin/dict', { headers: NO_CACHE_HEADERS })
    dicts.value = normalizeDictList(getResponseData(response, '字典数据待确认'))
    syncActiveDictFromList()
    dictsLoadState.value = 'ready'
  } catch (err: any) {
    dicts.value = null
    dictsLoadState.value = 'error'
    message.error(err.message || '加载数据字典失败')
  }
}

async function selectDict(d: DictRecord) {
  activeDict.value = d
  items.value = null
  itemsLoadState.value = 'loading'
  try {
    const response = await request.get<unknown>(`/api/admin/dict/${d.id}/items`, { headers: NO_CACHE_HEADERS })
    items.value = normalizeDictItemList(getResponseData(response, '字典条目待确认'))
    itemsLoadState.value = 'ready'
  } catch (err: any) {
    items.value = null
    itemsLoadState.value = 'error'
    message.error(err.message || '加载字典项失败')
  }
}

async function handleSaveDict() {
  if (!dictForm.code || !dictForm.name) { message.error('编码和名称为必填'); return false }
  try {
    const previousCount = dicts.value?.length
    if (editingDict.value) {
      const currentEditingId = editingDict.value
      const response = await request.put<unknown>(`/api/admin/dict/${currentEditingId}?name=${encodeURIComponent(dictForm.name)}&description=${encodeURIComponent(dictForm.description)}`)
      requireDict(getResponseData(response, '字典更新结果待确认'), 'update')
      await loadDicts()
      const refreshed = dicts.value?.find(item => Number(item.id) === currentEditingId)
      if (
        !refreshed
        || refreshed.dictName !== dictForm.name
        || (refreshed.description || '') !== dictForm.description
        || (typeof previousCount === 'number' && dicts.value?.length !== previousCount)
      ) {
        throw new Error('字典更新结果待确认')
      }
      if (activeDict.value?.id === currentEditingId) {
        activeDict.value = refreshed
      }
    } else {
      const response = await request.post<unknown>(`/api/admin/dict?code=${encodeURIComponent(dictForm.code)}&name=${encodeURIComponent(dictForm.name)}&description=${encodeURIComponent(dictForm.description)}`)
      const created = requireDict(getResponseData(response, '字典创建结果待确认'), 'create')
      await loadDicts()
      const refreshed = dicts.value?.find(item => Number(item.id) === created.id)
      if (
        !refreshed
        || refreshed.dictCode !== dictForm.code
        || refreshed.dictName !== dictForm.name
        || (refreshed.description || '') !== dictForm.description
        || (typeof previousCount === 'number' && typeof dicts.value?.length === 'number' && dicts.value.length < previousCount + 1)
      ) {
        throw new Error('字典创建结果待确认')
      }
    }
    message.success('已保存'); showDictEditor.value = false; return true
  } catch (err: any) { message.error(err.message || '操作失败'); return false }
}

async function handleDeleteDict(id: number) {
  try {
    const previousCount = dicts.value?.length
    await request.delete(`/api/admin/dict/${id}`)
    await loadDicts()
    if (dicts.value?.some(d => Number(d.id) === id)) {
      throw new Error('字典删除结果待确认')
    }
    if (typeof previousCount === 'number' && typeof dicts.value?.length === 'number' && dicts.value.length > Math.max(0, previousCount - 1)) {
      throw new Error('字典删除结果待确认')
    }
    if (activeDict.value?.id === id) {
      activeDict.value = null
      items.value = []
      itemsLoadState.value = 'ready'
    }
    message.success('已删除')
  }
  catch (err: any) { message.error(err.message || '删除失败') }
}

async function handleSaveItem() {
  if (!itemForm.code || !itemForm.name || !activeDict.value) { message.error('编码和名称为必填'); return false }
  try {
    const activeDictId = Number(activeDict.value.id)
    const previousCount = items.value?.length
    const currentActiveDict = activeDict.value
    if (!currentActiveDict) {
      throw new Error('字典条目待确认')
    }
    if (editingItem.value) {
      const currentEditingId = editingItem.value
      const response = await request.put<unknown>(`/api/admin/dict/items/${currentEditingId}?name=${encodeURIComponent(itemForm.name)}&sortOrder=${itemForm.sortOrder}`)
      requireDictItem(getResponseData(response, '条目更新结果待确认'), 'update')
      await selectDict(currentActiveDict)
      const refreshed = items.value?.find(item => Number(item.id) === currentEditingId)
      if (
        !refreshed
        || refreshed.itemName !== itemForm.name
        || Number(refreshed.sortOrder) !== itemForm.sortOrder
        || (typeof previousCount === 'number' && items.value?.length !== previousCount)
      ) {
        throw new Error('条目更新结果待确认')
      }
    } else {
      const response = await request.post<unknown>(`/api/admin/dict/${activeDictId}/items?code=${encodeURIComponent(itemForm.code)}&name=${encodeURIComponent(itemForm.name)}&sortOrder=${itemForm.sortOrder}`)
      const created = requireDictItem(getResponseData(response, '条目创建结果待确认'), 'create')
      await selectDict(currentActiveDict)
      const refreshed = items.value?.find(item => Number(item.id) === created.id)
      if (
        !refreshed
        || refreshed.itemCode !== itemForm.code
        || refreshed.itemName !== itemForm.name
        || Number(refreshed.sortOrder) !== itemForm.sortOrder
        || (typeof previousCount === 'number' && typeof items.value?.length === 'number' && items.value.length < previousCount + 1)
      ) {
        throw new Error('条目创建结果待确认')
      }
    }
    message.success('已保存'); showItemEditor.value = false; return true
  } catch (err: any) { message.error(err.message || '操作失败'); return false }
}

function editItem(item: DictItemRecord) {
  editingItem.value = item.id; itemForm.code = item.itemCode; itemForm.name = item.itemName; itemForm.sortOrder = item.sortOrder; showItemEditor.value = true
}

async function toggleItemStatus(item: DictItemRecord) {
  const current = normalizeBinaryStatus(item.status)
  if (current === null) { message.error('条目状态尚未明确，暂时无法切换'); return }
  const currentActiveDict = activeDict.value
  if (!currentActiveDict) { message.error('字典条目待确认'); return }
  try {
    const response = await request.put<unknown>(`/api/admin/dict/items/${item.id}?status=${current ? 0 : 1}`)
    requireDictItem(getResponseData(response, '条目更新结果待确认'), 'update')
    await selectDict(currentActiveDict)
    const refreshed = items.value?.find(entry => Number(entry.id) === Number(item.id))
    if (!refreshed || normalizeBinaryStatus(refreshed.status) !== !current) {
      throw new Error('条目状态待确认')
    }
  }
  catch (err: any) { message.error(err.message || '操作失败') }
}

function normalizeBinaryStatus(value: unknown): boolean | null {
  if (value === 1 || value === '1' || value === true || value === 'true') return true
  if (value === 0 || value === '0' || value === false || value === 'false') return false
  return null
}

async function handleDeleteItem(id: number) {
  const currentActiveDict = activeDict.value
  if (!currentActiveDict) { message.error('字典条目待确认'); return }
  try {
    const previousCount = items.value?.length
    await request.delete(`/api/admin/dict/items/${id}`)
    await selectDict(currentActiveDict)
    if (items.value?.some(i => Number(i.id) === id)) {
      throw new Error('条目删除结果待确认')
    }
    if (typeof previousCount === 'number' && typeof items.value?.length === 'number' && items.value.length > Math.max(0, previousCount - 1)) {
      throw new Error('条目删除结果待确认')
    }
    message.success('已删除')
  }
  catch (err: any) { message.error(err.message || '删除失败') }
}
</script>

<style scoped>
.admin-dict { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; color: #e5e7eb; }
.status-note { margin-bottom: 12px; font-size: 12px; color: #f59e0b; }
.dict-item { display: flex; align-items: center; justify-content: space-between; padding: 8px 10px; border-radius: 8px; cursor: pointer; margin-bottom: 4px; transition: background .2s; }
.dict-item:hover { background: rgba(255,255,255,0.03); }
.dict-item.active { background: rgba(16,185,129,0.06); border: 1px solid rgba(16,185,129,0.2); }
.dict-info { display: flex; flex-direction: column; }
.dict-name { font-size: 13px; color: #f3f4f6; }
.dict-code code { font-size: 11px; color: #6b7280; }
.dict-table { background: transparent !important; }
.dict-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; font-size: 12px; }
.dict-table td { color: #e5e7eb; padding: 6px 8px; font-size: 13px; }
.empty-cell { text-align: center; padding: 24px !important; color: #9ca3af; }
</style>
