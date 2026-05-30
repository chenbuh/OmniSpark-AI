<template>
  <div class="audit-container">
    <div class="page-header">
      <h2>审计日志 (Audit Logs)</h2>
      <p class="subtitle">系统操作审计记录，追踪所有用户的关键操作行为。</p>
    </div>

    <!-- 过滤器 -->
    <n-card class="glass-card filter-card" :bordered="false">
      <div class="filter-row">
        <n-space align="center" :size="16">
          <n-select
            v-model:value="actionFilter"
            :options="actionOptions"
            placeholder="操作类型"
            style="width: 160px;"
            clearable
            @update:value="onFilter"
          />
          <n-button type="primary" size="small" @click="loadLogs">刷新</n-button>
          <!-- 仅管理员可清理 N 天前日志 -->
          <template v-if="isAdmin">
            <n-divider vertical />
            <span class="cleanup-lbl">清理</span>
            <n-input-number v-model:value="cleanupDays" :min="7" :max="3650" size="small" style="width: 110px;">
              <template #suffix>天前</template>
            </n-input-number>
            <n-button type="error" size="small" tertiary :loading="cleaning" @click="handleCleanup">清理日志</n-button>
          </template>
        </n-space>
        <span class="count-lbl">共 {{ total }} 条记录</span>
      </div>
    </n-card>

    <!-- 日志表格 -->
    <n-card class="glass-card" :bordered="false" style="margin-top: 16px;">
      <n-table :single-line="false" class="audit-table">
        <thead>
          <tr>
            <th style="width: 80px;">ID</th>
            <th style="width: 100px;">用户</th>
            <th style="width: 140px;">操作</th>
            <th>详情</th>
            <th style="width: 120px;">IP</th>
            <th style="width: 160px;">时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td><code>#{{ log.id }}</code></td>
            <td>{{ log.username || '未知' }}</td>
            <td><n-tag size="small" :type="actionColor(log.action)">{{ formatAction(log.action) }}</n-tag></td>
            <td><n-ellipsis :line-clamp="1" :tooltip="true">{{ log.detail }}</n-ellipsis></td>
            <td><code>{{ log.ip || '-' }}</code></td>
            <td>{{ log.createdAt?.substring(0, 19)?.replace('T', ' ') }}</td>
          </tr>
          <tr v-if="logs.length === 0">
            <td colspan="6" class="empty-cell">暂无审计日志</td>
          </tr>
        </tbody>
      </n-table>
      <div class="pager" v-if="total > 0">
        <n-pagination
          v-model:page="page"
          :page-size="pageSize"
          :item-count="total"
          show-size-picker
          :page-sizes="pageSizeOptions"
          @update:page="loadLogs"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import request from '@/api/request'
import { useUserStore } from '@/store/user'

const message = useMessage()
const dialog = useDialog()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.userInfo?.role === 'admin')

const logs = ref<any[]>([])
const actionFilter = ref<string | null>(null)
const page = ref(1)
const pageSize = ref(20)
const pageSizeOptions = [20, 50, 100]
const total = ref(0)
const cleanupDays = ref(30)
const cleaning = ref(false)

const actionOptions = [
  { label: '全部', value: '' },
  { label: '登录', value: 'Auth_login' },
  { label: '注册', value: 'Auth_register' },
  { label: '退出', value: 'Auth_logout' },
  { label: '创建项目', value: 'Project_create' },
  { label: '删除项目', value: 'Project_delete' },
  { label: '创建团队', value: 'Team_create' },
  { label: '邀请成员', value: 'Team_inviteMember' },
  { label: '生图', value: 'Generation_image' },
  { label: '生视频', value: 'Generation_video' },
  { label: '上传资产', value: 'Asset_upload' },
  { label: '删除资产', value: 'Asset_delete' },
  { label: '添加模型', value: 'ModelProvider_create' },
  { label: '测试连接', value: 'ModelProvider_test' }
]

const actionColor = (action: string) => {
  if (!action) return 'default'
  if (action.includes('delete') || action.includes('remove')) return 'error'
  if (action.includes('create') || action.includes('add') || action.includes('register')) return 'success'
  if (action.includes('login') || action.includes('logout')) return 'info'
  if (action.includes('test') || action.includes('retry')) return 'warning'
  return 'default'
}

const formatAction = (action: string) => {
  if (!action) return '-'
  return action.replace(/_/g, ' ')
}

async function loadLogs() {
  try {
    // 普通用户仅查看本人审计日志，走统一 request 封装
    const params: Record<string, any> = { page: page.value, size: pageSize.value }
    if (actionFilter.value) params.action = actionFilter.value
    const res = await request.get('/api/audit-logs/my', { params })
    const data = (res as any).data || {}
    logs.value = data.records || []
    total.value = data.total || 0
  } catch (err: any) {
    logs.value = []
    message.error(err.message || '加载审计日志失败')
  }
}

// 切换操作类型过滤时回到第 1 页
function onFilter() {
  page.value = 1
  loadLogs()
}

// 切换每页条数时回到第 1 页
function handlePageSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  loadLogs()
}

// 管理员清理 N 天前的审计日志
function handleCleanup() {
  dialog.warning({
    title: '清理审计日志',
    content: `确定删除 ${cleanupDays.value} 天前的所有审计日志吗？此操作不可恢复。`,
    positiveText: '确定删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      cleaning.value = true
      try {
        const res = await request.delete('/api/audit-logs', { params: { daysOld: cleanupDays.value } })
        message.success(`已清理 ${(res as any).data ?? 0} 条审计日志`)
        page.value = 1
        await loadLogs()
      } catch (err: any) {
        message.error(err.message || '清理失败')
      } finally {
        cleaning.value = false
      }
    }
  })
}

onMounted(loadLogs)
</script>

<style scoped>
.audit-container { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card {
  background: rgba(15,23,42,0.4) !important;
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255,255,255,0.08) !important;
  border-radius: 16px !important;
}
.filter-row { display: flex; justify-content: space-between; align-items: center; }
.count-lbl { font-size: 12px; color: #9ca3af; }
.cleanup-lbl { font-size: 12px; color: #9ca3af; }

.audit-table { background-color: transparent !important; }
.audit-table th {
  background-color: rgba(255,255,255,0.02) !important;
  color: #9ca3af !important;
  border-bottom: 1px solid rgba(255,255,255,0.06) !important;
  font-size: 12px;
}
.audit-table td {
  border-bottom: 1px solid rgba(255,255,255,0.04) !important;
  color: #e5e7eb;
  padding: 12px 10px;
  font-size: 12px;
}
.empty-cell { text-align: center !important; padding: 40px !important; color: #6b7280; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
