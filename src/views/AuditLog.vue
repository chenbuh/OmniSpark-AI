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
          />
          <n-button type="primary" size="small" @click="loadLogs">刷新</n-button>
        </n-space>
        <span class="count-lbl">共 {{ logs.length }} 条记录</span>
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
          <tr v-for="log in filteredLogs" :key="log.id">
            <td><code>#{{ log.id }}</code></td>
            <td>{{ log.username || '未知' }}</td>
            <td><n-tag size="small" :type="actionColor(log.action)">{{ formatAction(log.action) }}</n-tag></td>
            <td><n-ellipsis :line-clamp="1" :tooltip="true">{{ log.detail }}</n-ellipsis></td>
            <td><code>{{ log.ip || '-' }}</code></td>
            <td>{{ log.createdAt?.substring(0, 19)?.replace('T', ' ') }}</td>
          </tr>
          <tr v-if="filteredLogs.length === 0">
            <td colspan="6" class="empty-cell">暂无审计日志</td>
          </tr>
        </tbody>
      </n-table>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMessage } from 'naive-ui'

const message = useMessage()
const logs = ref<any[]>([])
const actionFilter = ref<string | null>(null)

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

const filteredLogs = computed(() => {
  if (!actionFilter.value) return logs.value
  return logs.value.filter(l => l.action === actionFilter.value)
})

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
    const base = 'http://localhost:8080'
    const token = localStorage.getItem('satoken') || ''
    const res = await fetch(`${base}/api/audit-logs?page=0&size=200`, {
      headers: { 'satoken': token }
    })
    const json = await res.json()
    logs.value = json.data || []
  } catch {
    logs.value = []
    message.error('加载审计日志失败')
  }
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
</style>
