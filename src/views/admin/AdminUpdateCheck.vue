<template>
  <div class="admin-update">
    <div class="page-header">
      <h2>系统更新 (System Update)</h2>
      <p class="subtitle">查看当前构建信息，并从 GitHub 仓库检查真实更新状态。</p>
    </div>

    <n-row :gutter="20">
      <!-- 当前版本 -->
      <n-col :span="12">
        <n-card title="当前版本" class="glass-card" :bordered="false">
          <div v-if="versionLoadState === 'error'" class="status-note">当前版本信息待确认，请稍后重试。</div>
          <div v-else-if="versionLoadState === 'loading'" class="check-placeholder">
            <p>正在读取当前构建信息...</p>
          </div>
          <n-descriptions v-else-if="version" :column="1">
            <n-descriptions-item label="版本号"><n-tag type="success">{{ version.currentVersion }}</n-tag></n-descriptions-item>
            <n-descriptions-item label="构建时间">{{ version.buildTime }}</n-descriptions-item>
            <n-descriptions-item v-if="version.currentBranch" label="当前分支">{{ version.currentBranch }}</n-descriptions-item>
            <n-descriptions-item v-if="version.currentCommitShortSha" label="当前提交">
              <span class="mono">{{ version.currentCommitShortSha }}</span>
            </n-descriptions-item>
            <n-descriptions-item label="服务器时间">{{ version.serverTime }}</n-descriptions-item>
            <n-descriptions-item label="Java 版本">{{ version.javaVersion }}</n-descriptions-item>
            <n-descriptions-item label="操作系统">{{ version.osName }} ({{ version.osArch }})</n-descriptions-item>
            <n-descriptions-item label="更新源">
              <a v-if="version.repositoryUrl" :href="version.repositoryUrl" target="_blank" rel="noreferrer" class="link-text">
                {{ version.updateSource }}
              </a>
              <span v-else>{{ version.updateSource || '-' }}</span>
            </n-descriptions-item>
            <n-descriptions-item label="默认分支">{{ version.defaultBranch || '-' }}</n-descriptions-item>
          </n-descriptions>
          <n-empty v-else description="当前版本信息待确认，请稍后重试。" style="padding: 16px 0;" />
        </n-card>
      </n-col>

      <!-- 更新检查 -->
      <n-col :span="12">
        <n-card title="检查更新" class="glass-card" :bordered="false">
          <div v-if="updateCheckLoadState === 'error'" class="check-placeholder">
            <p>更新信息待确认，请稍后重试。</p>
            <n-button type="primary" @click="checkUpdate" :loading="checking">
              <template #icon><RefreshCw /></template>重新检查
            </n-button>
          </div>

          <div v-else-if="!updateCheck" class="check-placeholder">
            <p>点击下方按钮拉取仓库最新发布信息。</p>
            <n-button type="primary" @click="checkUpdate" :loading="checking">
              <template #icon><RefreshCw /></template>检查更新
            </n-button>
          </div>

          <div v-else class="update-result">
            <n-descriptions :column="1">
              <n-descriptions-item label="当前版本">{{ updateCheck.currentVersion }}</n-descriptions-item>
              <n-descriptions-item v-if="updateCheck.currentBranch" label="当前分支">{{ updateCheck.currentBranch }}</n-descriptions-item>
              <n-descriptions-item v-if="updateCheck.currentCommitShortSha" label="当前提交">
                <span class="mono">{{ updateCheck.currentCommitShortSha }}</span>
              </n-descriptions-item>
              <n-descriptions-item label="远程版本">{{ updateCheck.latestVersion || '-' }}</n-descriptions-item>
              <n-descriptions-item label="数据来源">{{ updateCheck.sourceLabel || '-' }}</n-descriptions-item>
              <n-descriptions-item label="检查时间">{{ formatDateTime(updateCheck.checkTime) }}</n-descriptions-item>
              <n-descriptions-item v-if="updateCheck.releasePublishedAt" label="发布时间">
                {{ formatDateTime(updateCheck.releasePublishedAt) }}
              </n-descriptions-item>
              <n-descriptions-item v-if="updateCheck.latestCommitShortSha" label="最新提交">
                <span class="mono">{{ updateCheck.latestCommitShortSha }}</span>
              </n-descriptions-item>
              <n-descriptions-item label="状态">
                <n-tag v-if="updateCheck.hasUpdate" type="error">发现可用更新</n-tag>
                <n-tag v-else-if="updateCheck.sourceType === 'commit' && !updateCheck.currentCommitShortSha" type="warning">当前提交待确认</n-tag>
                <n-tag v-else-if="updateCheck.sourceType === 'commit'" type="warning">仓库未发布版本</n-tag>
                <n-tag v-else type="success">已是最新版本</n-tag>
              </n-descriptions-item>
            </n-descriptions>

            <n-alert v-if="updateCheck.hasUpdate" type="warning" style="margin-top:12px;">
              <template #header>{{ updateCheck.sourceType === 'commit' ? '主分支有新提交可同步' : `新版本 ${updateCheck.latestVersion} 可用` }}</template>
              <div v-if="updateCheck.releaseNotes" class="notes-text">{{ updateCheck.releaseNotes }}</div>
              <div v-else-if="updateCheck.sourceType === 'commit' && updateCheck.latestCommitMessage" class="notes-text">{{ updateCheck.latestCommitMessage }}</div>
              <div class="action-row">
                <n-button v-if="updateCheck.downloadUrl" size="small" type="primary" @click="openUrl(updateCheck.downloadUrl)">下载安装包</n-button>
                <n-button size="small" tertiary type="primary" @click="openUrl(updateCheck.releaseUrl || updateCheck.repositoryUrl)">查看版本详情</n-button>
              </div>
            </n-alert>

            <n-alert v-else-if="updateCheck.sourceType === 'commit'" type="info" style="margin-top:12px;">
              <template #header>仓库尚未发布正式版本</template>
              <div v-if="updateCheck.latestCommitMessage" class="notes-text">{{ updateCheck.latestCommitMessage }}</div>
              <div class="action-row">
                <n-button size="small" type="primary" @click="openUrl(updateCheck.releaseUrl || updateCheck.repositoryUrl)">前往仓库</n-button>
              </div>
            </n-alert>

            <n-alert v-else-if="updateCheck.error" type="info" style="margin-top:12px;">
              {{ updateCheck.error }}
            </n-alert>

            <div class="action-row" style="margin-top: 12px;">
              <n-button size="small" secondary type="primary" @click="checkUpdate">重新检查</n-button>
              <n-button v-if="updateCheck.repositoryUrl" size="small" quaternary @click="openUrl(updateCheck.repositoryUrl)">打开仓库</n-button>
            </div>
          </div>
        </n-card>
      </n-col>
    </n-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { RefreshCw } from 'lucide-vue-next'
import request from '@/api/request'

type UpdateSourceType = 'release' | 'tag' | 'commit' | 'unknown'

interface VersionInfo {
  currentVersion: string
  buildTime: string
  currentBranch: string
  currentCommitSha: string
  currentCommitShortSha: string
  serverTime: string
  javaVersion: string
  osName: string
  osArch: string
  updateSource: string
  defaultBranch: string
  repositoryUrl: string
}

interface UpdateCheckInfo {
  currentVersion: string
  currentBranch: string
  currentCommitSha: string
  currentCommitShortSha: string
  latestVersion: string
  sourceType: UpdateSourceType
  sourceLabel: string
  checkTime: string
  releasePublishedAt: string
  latestCommitShortSha: string
  latestCommitMessage: string
  hasUpdate: boolean
  releaseUrl: string
  repositoryUrl: string
  releaseNotes: string
  downloadUrl: string
  error: string
}

const message = useMessage()
const version = ref<VersionInfo | null>(null)
const updateCheck = ref<UpdateCheckInfo | null>(null)
const checking = ref(false)
const versionLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const updateCheckLoadState = ref<'loading' | 'ready' | 'error'>('loading')

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string) {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function normalizeOptionalText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeSourceType(value: unknown): UpdateSourceType {
  const normalized = normalizeOptionalText(value)
  if (normalized === 'release' || normalized === 'tag' || normalized === 'commit' || normalized === 'unknown') {
    return normalized
  }
  throw new Error('更新信息待确认')
}

async function loadVersion() {
  versionLoadState.value = 'loading'
  try {
    const response = await request.get<unknown>('/api/admin/version')
    version.value = normalizeVersionInfo(getResponseData(response, '当前版本信息待确认'))
    versionLoadState.value = 'ready'
  } catch (err: any) {
    version.value = null
    versionLoadState.value = 'error'
    message.error(err.message || '当前版本信息待确认')
  }
}

async function checkUpdate() {
  checking.value = true
  updateCheckLoadState.value = 'loading'
  try {
    const response = await request.get<unknown>('/api/admin/version/check', {
      params: { refresh: true }
    })
    updateCheck.value = normalizeUpdateCheckInfo(getResponseData(response, '更新信息待确认'))
    updateCheckLoadState.value = 'ready'
  } catch (err: any) {
    updateCheck.value = null
    updateCheckLoadState.value = 'error'
    message.error(err.message || '更新信息待确认')
  } finally {
    checking.value = false
  }
}

function openUrl(url?: string) {
  if (url) {
    window.open(url, '_blank', 'noopener,noreferrer')
  }
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function normalizeVersionInfo(payload: unknown): VersionInfo {
  if (!isPlainObject(payload)) {
    throw new Error('当前版本信息待确认')
  }
  const currentVersion = normalizeOptionalText(payload.currentVersion)
  const buildTime = normalizeOptionalText(payload.buildTime)
  const serverTime = normalizeOptionalText(payload.serverTime)
  const javaVersion = normalizeOptionalText(payload.javaVersion)
  const osName = normalizeOptionalText(payload.osName)
  const osArch = normalizeOptionalText(payload.osArch)
  const updateSource = normalizeOptionalText(payload.updateSource)
  if (!currentVersion || !buildTime || !serverTime || !javaVersion || !osName || !osArch || !updateSource) {
    throw new Error('当前版本信息待确认')
  }

  return {
    currentVersion,
    buildTime,
    currentBranch: normalizeOptionalText(payload.currentBranch),
    currentCommitSha: normalizeOptionalText(payload.currentCommitSha),
    currentCommitShortSha: normalizeOptionalText(payload.currentCommitShortSha),
    serverTime,
    javaVersion,
    osName,
    osArch,
    updateSource,
    defaultBranch: normalizeOptionalText(payload.defaultBranch),
    repositoryUrl: normalizeOptionalText(payload.repositoryUrl)
  }
}

function normalizeUpdateCheckInfo(payload: unknown): UpdateCheckInfo {
  if (!isPlainObject(payload)) {
    throw new Error('更新信息待确认')
  }
  const currentVersion = normalizeOptionalText(payload.currentVersion)
  const sourceType = normalizeSourceType(payload.sourceType)
  const sourceLabel = normalizeOptionalText(payload.sourceLabel)
  const checkTime = normalizeOptionalText(payload.checkTime)
  if (!currentVersion || !sourceLabel || !checkTime) {
    throw new Error('更新信息待确认')
  }

  return {
    currentVersion,
    currentBranch: normalizeOptionalText(payload.currentBranch),
    currentCommitSha: normalizeOptionalText(payload.currentCommitSha),
    currentCommitShortSha: normalizeOptionalText(payload.currentCommitShortSha),
    latestVersion: normalizeOptionalText(payload.latestVersion),
    sourceType,
    sourceLabel,
    checkTime,
    releasePublishedAt: normalizeOptionalText(payload.releasePublishedAt),
    latestCommitShortSha: normalizeOptionalText(payload.latestCommitShortSha),
    latestCommitMessage: normalizeOptionalText(payload.latestCommitMessage),
    hasUpdate: typeof payload.hasUpdate === 'boolean' ? payload.hasUpdate : false,
    releaseUrl: normalizeOptionalText(payload.releaseUrl),
    repositoryUrl: normalizeOptionalText(payload.repositoryUrl),
    releaseNotes: normalizeOptionalText(payload.releaseNotes),
    downloadUrl: normalizeOptionalText(payload.downloadUrl),
    error: normalizeOptionalText(payload.error)
  }
}

onMounted(() => {
  void loadVersion()
  void checkUpdate()
})
</script>

<style scoped>
.admin-update { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-muted); margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.check-placeholder { text-align: center; padding: 20px; color: #9ca3af; display: flex; flex-direction: column; align-items: center; gap: 16px; }
.status-note { font-size: 12px; color: #fca5a5; }
.update-result { display: flex; flex-direction: column; gap: 8px; }
.notes-text { font-size: 12px; line-height: 1.7; margin-bottom: 10px; white-space: pre-wrap; }
.action-row { display: flex; flex-wrap: wrap; gap: 8px; }
.link-text { color: #10b981; text-decoration: none; }
.link-text:hover { text-decoration: underline; }
.mono { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
</style>
