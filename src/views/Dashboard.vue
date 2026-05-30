<template>
  <div class="dashboard-container">
    <!-- 系统公告 -->
    <n-alert v-if="announcement" type="info" closable style="margin-bottom:16px;">
      <template #header>{{ announcement.title }}</template>
      {{ announcement.content }}
    </n-alert>

    <div class="welcome-header">
      <h1>欢迎回来，{{ userStore.userInfo?.nickname }} 👋</h1>
      <p class="subtitle">在此统一管理真实项目资产、模型提供商与图像视频生成任务。</p>
    </div>

    <!-- 加载骨架 -->
    <SkeletonCard v-if="loading" type="chart" />

    <!-- 数据汇总卡片 -->
    <n-row :gutter="24" class="stats-row" v-if="!loading">
      <n-col :span="8">
        <n-card class="stat-card glass-card purple-glow">
          <div class="card-inner">
            <div class="text-group">
              <span class="label">进行中的生成任务</span>
              <span class="value">{{ activeTasksCount }} <span class="unit">个</span></span>
            </div>
            <div class="icon-box purple">
              <Activity class="stat-icon" />
            </div>
          </div>
          <div class="asset-details-text">
            在当前的并发队列中，有 {{ activeTasksCount }} 个任务处于渲染排队中
          </div>
        </n-card>
      </n-col>

      <n-col :span="8">
        <n-card class="stat-card glass-card blue-glow">
          <div class="card-inner">
            <div class="text-group">
              <span class="label">当前空间资产</span>
              <span class="value">{{ currentAssets.length }} <span class="unit">个</span></span>
            </div>
            <div class="icon-box blue">
              <Library class="stat-icon" />
            </div>
          </div>
          <div class="asset-details-text">
            包含 {{ currentAssets.filter(a => a.assetType === 'image').length }} 张图片，{{ currentAssets.filter(a => a.assetType === 'video').length }} 个视频
          </div>
        </n-card>
      </n-col>

      <n-col :span="8">
        <n-card class="stat-card glass-card green-glow">
          <div class="card-inner">
            <div class="text-group">
              <span class="label">任务生成成功率</span>
              <span class="value">98.4%</span>
            </div>
            <div class="icon-box green">
              <CheckCircle class="stat-icon" />
            </div>
          </div>
          <div class="asset-details-text success">
            今日生成任务正常运行中，暂无积压
          </div>
        </n-card>
      </n-col>
    </n-row>

    <!-- 快捷通道 -->
    <n-row :gutter="24" class="quick-row">
      <n-col :span="12">
        <div class="quick-card image-channel" @click="router.push('/generate/image')">
          <div class="channel-content">
            <h3>文生图 / 图生图</h3>
            <p>使用当前空间已配置的图像模型，直接发起真实生图任务。</p>
            <div class="arrow-btn">
              <ArrowRight class="btn-icon" />
            </div>
          </div>
          <Image class="bg-icon" />
        </div>
      </n-col>
      <n-col :span="12">
        <div class="quick-card video-channel" @click="router.push('/generate/video')">
          <div class="channel-content">
            <h3>生视频中心</h3>
            <p>使用当前空间已配置的视频模型，发起文生视频或图生视频任务。</p>
            <div class="arrow-btn">
              <ArrowRight class="btn-icon" />
            </div>
          </div>
          <Video class="bg-icon" />
        </div>
      </n-col>
    </n-row>

    <n-row :gutter="24" style="margin-top: 24px;">
      <!-- 最近任务 -->
      <n-col :span="16">
        <n-card title="最近任务队列" class="glass-card" :bordered="false">
          <template #header-extra>
            <n-button text type="primary" @click="router.push('/tasks')">查看全部</n-button>
          </template>

          <n-table :single-line="false" class="tasks-table">
            <thead>
              <tr>
                <th>任务ID</th>
                <th>生成类型</th>
                <th>所用模型</th>
                <th>提示词描述</th>
                <th>生成状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in recentTasks" :key="task.id">
                <td>#{{ task.id.toString().substring(task.id.toString().length - 4) }}</td>
                <td>
                  <n-tag :type="task.taskType === 'image' ? 'success' : 'info'" size="small">
                    {{ task.taskType === 'image' ? '生图' : '视频' }}
                  </n-tag>
                </td>
                <td><code>{{ task.modelName }}</code></td>
                <td>
                  <n-ellipsis style="max-width: 260px" :tooltip="true">
                    {{ task.prompt }}
                  </n-ellipsis>
                </td>
                <td>
                  <n-tag
                    :type="task.status === 'success' ? 'success' : task.status === 'running' ? 'warning' : 'default'"
                    size="small"
                  >
                    {{ task.status === 'success' ? '生成成功' : task.status === 'running' ? '进行中' : '队列排队' }}
                  </n-tag>
                </td>
                <td>
                  <n-space>
                    <n-button type="primary" size="tiny" secondary @click="handleReuse(task)">复用</n-button>
                    <n-button type="error" size="tiny" tertiary @click="handleDelete(task.id)">删除</n-button>
                  </n-space>
                </td>
              </tr>
              <tr v-if="recentTasks.length === 0">
                <td colspan="6" style="text-align: center; color: #9ca3af;">暂无任务，请前往生图或视频页创建！</td>
              </tr>
            </tbody>
          </n-table>
        </n-card>
      </n-col>

      <!-- 推荐提示词 -->
      <n-col :span="8">
        <n-card title="灵感提示词推荐" class="glass-card" :bordered="false">
          <div class="templates-list">
            <div
              v-for="tpl in recommendedTemplates"
              :key="tpl.id"
              class="tpl-item"
              @click="handleApplyTemplate(tpl.content)"
            >
              <div class="tpl-head">
                <span class="tpl-name">{{ tpl.name }}</span>
                <n-tag size="mini" type="warning" round>{{ tpl.tag }}</n-tag>
              </div>
              <p class="tpl-content">{{ tpl.content }}</p>
            </div>
          </div>
        </n-card>
      </n-col>
    </n-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useUserStore } from '@/store/user'
import { useProjectStore } from '@/store/project'
import { useTaskStore } from '@/store/task'
import { useAssetStore } from '@/store/asset'
import { templateApi, type PromptTemplate } from '@/api/templates'
import SkeletonCard from '@/components/SkeletonCard.vue'
import request from '@/api/request'
import {
  Activity,
  Library,
  CheckCircle,
  ArrowRight,
  Image,
  Video
} from 'lucide-vue-next'

const router = useRouter()
const message = useMessage()

const userStore = useUserStore()
const projectStore = useProjectStore()
const taskStore = useTaskStore()
const assetStore = useAssetStore()

const loading = ref(true)
const announcement = ref<any>(null)
const recommendedTemplates = ref<PromptTemplate[]>([])

// 获取当前空间下的资产
const currentAssets = computed(() => {
  return assetStore.getAssetsByProject(projectStore.activeProjectId)
})

// 获取最近的 3 个任务
const recentTasks = computed(() => {
  return taskStore.getTasksByProject(projectStore.activeProjectId).slice(0, 3)
})

// 获取当前处于进行中（排队或运行）的任务数
const activeTasksCount = computed(() => {
  return taskStore.getTasksByProject(projectStore.activeProjectId).filter(t => t.status === 'pending' || t.status === 'running').length
})

onMounted(async () => {
  try {
    const [tplRes, annRes] = await Promise.all([
      templateApi.getTemplates(projectStore.activeProjectId),
      request.get('/api/announcements/active')
    ])
    recommendedTemplates.value = tplRes.data.slice(0, 3)
    const anns = (annRes as any).data || []
    announcement.value = anns.length > 0 ? anns[0] : null
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})

watch(() => projectStore.activeProjectId, async () => {
  try {
    const res = await templateApi.getTemplates(projectStore.activeProjectId)
    recommendedTemplates.value = res.data.slice(0, 3)
  } catch (e) {
    console.error(e)
  }
})

// 复用提示词
const handleReuse = (task: any) => {
  if (task.taskType === 'image') {
    router.push({
      path: '/generate/image',
      query: { prompt: task.prompt, negPrompt: task.negativePrompt, model: task.modelName }
    })
  } else {
    router.push({
      path: '/generate/video',
      query: { prompt: task.prompt, model: task.modelName }
    })
  }
  message.success('已将参数复用至生成面板！')
}

// 删除任务
const handleDelete = async (id: number) => {
  await taskStore.deleteTask(id)
  message.success('任务删除成功')
}

// 应用提示词模板
const handleApplyTemplate = (content: string) => {
  router.push({
    path: '/generate/image',
    query: { prompt: content }
  })
  message.success('提示词已导入生图面板')
}
</script>

<style scoped>
.dashboard-container {
  padding-bottom: 40px;
}

.welcome-header {
  margin-bottom: 28px;
}

.welcome-header h1 {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 6px 0;
  color: #fff;
}

.subtitle {
  font-size: 14px;
  color: #9ca3af;
  margin: 0;
}

/* 磨砂玻璃卡片 */
.glass-card {
  background: rgba(15, 23, 42, 0.4) !important;
  backdrop-filter: blur(16px);
  border: 1px solid var(--border-color) !important;
  border-radius: 16px !important;
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.2);
}

.stat-card {
  padding: 10px 0;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
}

/* 霓虹发光阴影效果 */
.purple-glow:hover { box-shadow: 0 0 20px rgba(139, 92, 246, 0.15); }
.blue-glow:hover { box-shadow: 0 0 20px rgba(59, 130, 246, 0.15); }
.green-glow:hover { box-shadow: 0 0 20px rgba(16, 185, 129, 0.15); }

.card-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.text-group {
  display: flex;
  flex-direction: column;
}

.label {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.value {
  font-size: 32px;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1;
}

.unit {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-muted);
}

.icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 12px;
}

.icon-box.purple { background-color: rgba(139, 92, 246, 0.15); color: #a78bfa; }
.icon-box.blue { background-color: rgba(59, 130, 246, 0.15); color: #60a5fa; }
.icon-box.green { background-color: rgba(16, 185, 129, 0.15); color: #34d399; }

.stat-icon {
  width: 22px;
  height: 22px;
}

.stat-progress {
  margin-top: 20px;
}

.asset-details-text {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 18px;
}

.asset-details-text.success {
  color: #34d399;
}

/* 快捷通道设计 */
.quick-row {
  margin-top: 24px;
}

.quick-card {
  position: relative;
  height: 120px;
  padding: 24px;
  border-radius: 16px;
  cursor: pointer;
  overflow: hidden;
  display: flex;
  align-items: center;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.image-channel {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(59, 130, 246, 0.05) 100%);
  border: 1px solid rgba(16, 185, 129, 0.2);
}

.image-channel:hover {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.15) 0%, rgba(59, 130, 246, 0.08) 100%);
  border-color: rgba(16, 185, 129, 0.4);
  box-shadow: 0 8px 30px rgba(16, 185, 129, 0.15);
  transform: scale(1.01);
}

.video-channel {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.1) 0%, rgba(139, 92, 246, 0.05) 100%);
  border: 1px solid rgba(59, 130, 246, 0.2);
}

.video-channel:hover {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.15) 0%, rgba(139, 92, 246, 0.08) 100%);
  border-color: rgba(59, 130, 246, 0.4);
  box-shadow: 0 8px 30px rgba(59, 130, 246, 0.15);
  transform: scale(1.01);
}

.channel-content {
  z-index: 2;
}

.channel-content h3 {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 6px 0;
  color: #fff;
}

.channel-content p {
  font-size: 13px;
  color: #9ca3af;
  margin: 0 0 16px 0;
}

.arrow-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  transition: all 0.3s;
}

.quick-card:hover .arrow-btn {
  background: #fff;
  color: #000;
  transform: translateX(4px);
}

.btn-icon {
  width: 14px;
  height: 14px;
}

.bg-icon {
  position: absolute;
  right: 20px;
  bottom: -15px;
  width: 100px;
  height: 100px;
  opacity: 0.04;
  color: #fff;
  z-index: 1;
}

/* 表格定制 */
.tasks-table {
  background-color: transparent !important;
}

.tasks-table th {
  background-color: rgba(128, 128, 128, 0.02) !important;
  color: var(--text-muted) !important;
  border-bottom: 1px solid var(--border-color) !important;
}

.tasks-table td {
  border-bottom: 1px solid rgba(255, 255, 255, 0.04) !important;
  color: #e5e7eb;
}

/* 推荐提示词项 */
.templates-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tpl-item {
  padding: 14px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s;
}

.tpl-item:hover {
  background: rgba(128, 128, 128, 0.05);
  border-color: rgba(128, 128, 128, 0.2);
  transform: translateX(2px);
}

.tpl-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.tpl-name {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}

.tpl-content {
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
</style>
