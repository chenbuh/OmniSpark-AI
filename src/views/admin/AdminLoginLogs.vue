<template>
  <div class="admin-login-logs">
    <div class="page-header">
      <h2>登录日志 (Login Logs)</h2>
      <p class="subtitle">查看所有用户的登录记录。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <n-table :single-line="false" class="admin-table">
        <thead>
          <tr><th style="width:60px">ID</th><th style="width:80px">用户ID</th><th>用户名</th><th style="width:140px">IP</th><th>User Agent</th><th style="width:160px">登录时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td><code>#{{ log.id }}</code></td>
            <td>{{ log.userId }}</td>
            <td>{{ log.username }}</td>
            <td><code>{{ log.ip }}</code></td>
            <td><n-ellipsis :line-clamp="1" :tooltip="true">{{ log.userAgent }}</n-ellipsis></td>
            <td>{{ log.createdAt?.substring(0, 19)?.replace('T', ' ') }}</td>
          </tr>
          <tr v-if="logs.length === 0"><td colspan="6" class="empty">暂无登录记录</td></tr>
        </tbody>
      </n-table>
      <div class="pager" v-if="total > pageSize">
        <n-pagination v-model:page="page" :page-size="pageSize" :item-count="total" @update:page="loadLogs" />
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import request from '@/api/request'

const message = useMessage()
const logs = ref<any[]>([])
const page = ref(1)
const pageSize = 20
const total = ref(0)

async function loadLogs() {
  try {
    const res = await request.get('/api/admin/login-logs', { params: { page: page.value, pageSize } })
    const data = (res as any).data || {}
    logs.value = data.records || []
    total.value = data.total || 0
  } catch (err: any) {
    logs.value = []
    message.error(err.message || '加载登录日志失败')
  }
}

onMounted(loadLogs)
</script>

<style scoped>
.admin-login-logs { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.admin-table { background: transparent !important; }
.admin-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; border-bottom: 1px solid rgba(255,255,255,0.06) !important; font-size: 12px; }
.admin-table td { border-bottom: 1px solid rgba(255,255,255,0.04) !important; color: #e5e7eb; padding: 8px; font-size: 12px; }
.empty { text-align: center; padding: 30px; color: #6b7280; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
