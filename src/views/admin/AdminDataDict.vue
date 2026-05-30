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
              <span>字典列表 ({{ dicts.length }})</span>
              <n-button size="tiny" primary @click="showDictEditor = true; editingDict = null; Object.assign(dictForm, { code: '', name: '', description: '' })">
                <template #icon><Plus /></template>
              </n-button>
            </div>
          </template>
          <div v-for="d in dicts" :key="d.id" class="dict-item" :class="{ active: activeDict?.id === d.id }" @click="selectDict(d)">
            <div class="dict-info">
              <span class="dict-name">{{ d.dictName }}</span>
              <span class="dict-code"><code>{{ d.dictCode }}</code></span>
            </div>
            <n-button size="tiny" text type="error" @click.stop="handleDeleteDict(d.id)">×</n-button>
          </div>
          <n-empty v-if="dicts.length === 0" description="暂无字典" style="padding:20px" />
        </n-card>
      </n-col>

      <!-- 右侧条目列表 -->
      <n-col :span="16">
        <n-card class="glass-card" :bordered="false" v-if="activeDict">
          <template #header>
            <div class="card-header">
              <span>{{ activeDict.dictName }} — 条目 ({{ items.length }})</span>
              <n-space>
                <n-button size="tiny" @click="showDictEditor = true; editingDict = activeDict.id; Object.assign(dictForm, { code: activeDict.dictCode, name: activeDict.dictName, description: activeDict.description || '' })">编辑字典</n-button>
                <n-button size="tiny" primary @click="showItemEditor = true; editingItem = null; Object.assign(itemForm, { code: '', name: '', sortOrder: 0 })">添加条目</n-button>
              </n-space>
            </div>
          </template>

          <n-table :single-line="false" class="dict-table">
            <thead>
              <tr><th style="width:80px">排序</th><th>编码</th><th>名称</th><th style="width:80px">状态</th><th style="width:140px">操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.id">
                <td>{{ item.sortOrder }}</td>
                <td><code>{{ item.itemCode }}</code></td>
                <td>{{ item.itemName }}</td>
                <td><n-switch :value="item.status === 1" @update:value="toggleItemStatus(item)" size="small" /></td>
                <td>
                  <n-space>
                    <n-button size="tiny" secondary @click="editItem(item)">编辑</n-button>
                    <n-button size="tiny" type="error" tertiary @click="handleDeleteItem(item.id)">删除</n-button>
                  </n-space>
                </td>
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
import { ref, reactive, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import request from '@/api/request'

const message = useMessage()
const dicts = ref<any[]>([])
const activeDict = ref<any>(null)
const items = ref<any[]>([])
const showDictEditor = ref(false)
const editingDict = ref<any>(null)
const dictForm = reactive({ code: '', name: '', description: '' })
const showItemEditor = ref(false)
const editingItem = ref<any>(null)
const itemForm = reactive({ code: '', name: '', sortOrder: 0 })

onMounted(loadDicts)

async function loadDicts() {
  try { const res = await request.get('/api/admin/dict'); dicts.value = (res as any).data || [] } catch {}
}

async function selectDict(d: any) {
  activeDict.value = d
  try {
    const res = await request.get(`/api/admin/dict/${d.id}/items`)
    items.value = (res as any).data || []
  } catch { items.value = [] }
}

async function handleSaveDict() {
  if (!dictForm.code || !dictForm.name) { message.error('编码和名称为必填'); return false }
  try {
    if (editingDict) {
      await request.put(`/api/admin/dict/${editingDict}?name=${encodeURIComponent(dictForm.name)}&description=${encodeURIComponent(dictForm.description)}`)
    } else {
      await request.post(`/api/admin/dict?code=${encodeURIComponent(dictForm.code)}&name=${encodeURIComponent(dictForm.name)}&description=${encodeURIComponent(dictForm.description)}`)
    }
    message.success('已保存'); showDictEditor.value = false; await loadDicts(); return true
  } catch { message.error('操作失败'); return false }
}

async function handleDeleteDict(id: number) {
  try { await request.delete(`/api/admin/dict/${id}`); dicts.value = dicts.value.filter(d => d.id !== id); if (activeDict.value?.id === id) activeDict.value = null; message.success('已删除') }
  catch { message.error('删除失败') }
}

async function handleSaveItem() {
  if (!itemForm.code || !itemForm.name || !activeDict.value) { message.error('编码和名称为必填'); return false }
  try {
    if (editingItem) {
      await request.put(`/api/admin/dict/items/${editingItem}?name=${encodeURIComponent(itemForm.name)}&sortOrder=${itemForm.sortOrder}`)
    } else {
      await request.post(`/api/admin/dict/${activeDict.value.id}/items?code=${encodeURIComponent(itemForm.code)}&name=${encodeURIComponent(itemForm.name)}&sortOrder=${itemForm.sortOrder}`)
    }
    message.success('已保存'); showItemEditor.value = false; await selectDict(activeDict.value); return true
  } catch { message.error('操作失败'); return false }
}

function editItem(item: any) {
  editingItem.value = item.id; itemForm.code = item.itemCode; itemForm.name = item.itemName; itemForm.sortOrder = item.sortOrder; showItemEditor.value = true
}

async function toggleItemStatus(item: any) {
  try { await request.put(`/api/admin/dict/items/${item.id}?status=${item.status === 1 ? 0 : 1}`); item.status = item.status === 1 ? 0 : 1 }
  catch { message.error('操作失败') }
}

async function handleDeleteItem(id: number) {
  try { await request.delete(`/api/admin/dict/items/${id}`); items.value = items.value.filter(i => i.id !== id); message.success('已删除') }
  catch { message.error('删除失败') }
}
</script>

<style scoped>
.admin-dict { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; color: #e5e7eb; }
.dict-item { display: flex; align-items: center; justify-content: space-between; padding: 8px 10px; border-radius: 8px; cursor: pointer; margin-bottom: 4px; transition: background .2s; }
.dict-item:hover { background: rgba(255,255,255,0.03); }
.dict-item.active { background: rgba(16,185,129,0.06); border: 1px solid rgba(16,185,129,0.2); }
.dict-info { display: flex; flex-direction: column; }
.dict-name { font-size: 13px; color: #f3f4f6; }
.dict-code code { font-size: 11px; color: #6b7280; }
.dict-table { background: transparent !important; }
.dict-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; font-size: 12px; }
.dict-table td { color: #e5e7eb; padding: 6px 8px; font-size: 13px; }
</style>
