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
              <n-descriptions-item v-if="updateCheck.sourceType === 'commit'" label="远程分支">
                {{ updateCheck.latestRefName || updateCheck.defaultBranch || '-' }}
              </n-descriptions-item>
              <n-descriptions-item v-else label="远程版本">{{ updateCheck.latestVersion || '-' }}</n-descriptions-item>
              <n-descriptions-item label="数据来源">{{ updateCheck.sourceLabel || '-' }}</n-descriptions-item>
              <n-descriptions-item label="检查时间">{{ formatDateTime(updateCheck.checkTime) }}</n-descriptions-item>
              <n-descriptions-item v-if="updateCheck.releasePublishedAt" label="发布时间">
                {{ formatDateTime(updateCheck.releasePublishedAt) }}
              </n-descriptions-item>
              <n-descriptions-item v-if="updateCheck.latestCommitShortSha" :label="updateCheck.sourceType === 'commit' ? '远程提交' : '最新提交'">
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

    <n-row :gutter="20" style="margin-top: 20px;">
      <n-col :span="24">
        <n-card title="最近更新动态" class="glass-card" :bordered="false">
          <template #header-extra>
            <n-button size="small" tertiary type="primary" @click="loadUpdateHistory(true)" :loading="historyChecking">
              <template #icon><RefreshCw /></template>刷新动态
            </n-button>
          </template>

          <div v-if="historyLoadState === 'error'" class="check-placeholder">
            <p>更新动态待确认，请稍后重试。</p>
            <n-button type="primary" @click="loadUpdateHistory(true)" :loading="historyChecking">
              <template #icon><RefreshCw /></template>重新加载
            </n-button>
          </div>

          <div v-else-if="historyLoadState === 'loading'" class="check-placeholder">
            <p>正在读取最近更新动态...</p>
          </div>

          <div v-else-if="updateHistory?.items.length" class="history-wrap">
            <div class="history-summary">
              <span>{{ updateHistory.sourceLabel || '更新记录' }}</span>
              <span>共 {{ updateHistory.total }} 条</span>
              <span>更新时间 {{ formatDateTime(updateHistory.checkTime) }}</span>
            </div>

            <div class="history-list">
              <div v-for="(item, index) in updateHistory.items" :key="`${item.sourceType}-${item.version || item.shortSha || index}`" class="history-item">
                <div class="history-item-head">
                  <div class="history-title-row">
                    <n-tag v-if="item.latest" size="small" type="error">最新</n-tag>
                    <n-tag v-else-if="item.installed" size="small" type="success">当前</n-tag>
                    <n-tag v-if="item.prerelease" size="small" type="warning">预发布</n-tag>
                    <n-tag v-if="item.draft" size="small">草稿</n-tag>
                    <strong class="history-title">{{ formatHistoryTitle(item) }}</strong>
                  </div>
                  <div class="action-row">
                    <n-button v-if="item.downloadUrl" size="tiny" type="primary" @click="openUrl(item.downloadUrl)">下载</n-button>
                    <n-button size="tiny" tertiary type="primary" @click="openUrl(item.url || updateHistory?.repositoryUrl)">查看详情</n-button>
                  </div>
                </div>

                <div class="history-meta">
                  <span>{{ formatDateTime(item.publishedAt) }}</span>
                  <span v-if="item.author">作者 {{ item.author }}</span>
                  <span v-if="item.refName">分支 {{ item.refName }}</span>
                </div>

                <div v-if="formatHistorySubtitle(item)" class="history-subtitle">{{ formatHistorySubtitle(item) }}</div>
                <div v-if="formatHistoryBody(item)" class="notes-text history-body">{{ formatHistoryBody(item) }}</div>
              </div>
            </div>

            <n-alert v-if="updateHistory.error" type="info" style="margin-top:12px;">
              {{ updateHistory.error }}
            </n-alert>
          </div>

          <n-empty v-else description="暂无可读取的更新动态" style="padding: 16px 0;" />
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
  latestRefName: string
  defaultBranch: string
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

interface UpdateHistoryItem {
  sourceType: UpdateSourceType
  version: string
  rawTag: string
  name: string
  publishedAt: string
  url: string
  notes: string
  draft: boolean
  prerelease: boolean
  downloadUrl: string
  refName: string
  sha: string
  shortSha: string
  message: string
  author: string
  installed: boolean
  latest: boolean
}

interface UpdateHistoryInfo {
  sourceType: UpdateSourceType
  sourceLabel: string
  checkTime: string
  repositoryUrl: string
  total: number
  items: UpdateHistoryItem[]
  error: string
}

const message = useMessage()
const version = ref<VersionInfo | null>(null)
const updateCheck = ref<UpdateCheckInfo | null>(null)
const checking = ref(false)
const historyChecking = ref(false)
const versionLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const updateCheckLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const updateHistory = ref<UpdateHistoryInfo | null>(null)
const historyLoadState = ref<'loading' | 'ready' | 'error'>('loading')

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

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return fallback
}

async function loadVersion() {
  versionLoadState.value = 'loading'
  try {
    const response = await request.get<unknown>('/api/admin/version')
    version.value = normalizeVersionInfo(getResponseData(response, '当前版本信息待确认'))
    versionLoadState.value = 'ready'
  } catch (err: unknown) {
    version.value = null
    versionLoadState.value = 'error'
    message.error(getErrorMessage(err, '当前版本信息待确认'))
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
  } catch (err: unknown) {
    updateCheck.value = null
    updateCheckLoadState.value = 'error'
    message.error(getErrorMessage(err, '更新信息待确认'))
  } finally {
    checking.value = false
  }
}

async function loadUpdateHistory(refresh = false) {
  historyChecking.value = refresh
  historyLoadState.value = 'loading'
  try {
    const response = await request.get<unknown>('/api/admin/version/history', {
      params: { refresh, limit: 6 }
    })
    updateHistory.value = normalizeUpdateHistoryInfo(getResponseData(response, '更新动态待确认'))
    historyLoadState.value = 'ready'
  } catch (err: unknown) {
    updateHistory.value = null
    historyLoadState.value = 'error'
    message.error(getErrorMessage(err, '更新动态待确认'))
  } finally {
    historyChecking.value = false
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

function formatHistoryTitle(item: UpdateHistoryItem) {
  if (item.sourceType === 'release') {
    return item.version || item.rawTag || item.name || '未命名发布'
  }
  return item.shortSha || item.sha || '提交待确认'
}

function formatHistorySubtitle(item: UpdateHistoryItem) {
  if (item.sourceType === 'release') {
    return item.name && item.name !== formatHistoryTitle(item) ? item.name : ''
  }
  return item.message.split('\n')[0]?.trim() || ''
}

function formatHistoryBody(item: UpdateHistoryItem) {
  if (item.sourceType === 'release') {
    return item.notes
  }
  const lines = item.message
    .split('\n')
    .map(line => line.trim())
    .filter(Boolean)
  if (lines.length <= 1) {
    return ''
  }
  return lines.slice(1).join('\n')
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
    latestRefName: normalizeOptionalText(payload.latestRefName),
    defaultBranch: normalizeOptionalText(payload.defaultBranch),
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

function normalizeUpdateHistoryInfo(payload: unknown): UpdateHistoryInfo {
  if (!isPlainObject(payload)) {
    throw new Error('更新动态待确认')
  }
  const sourceType = normalizeSourceType(payload.sourceType)
  const sourceLabel = normalizeOptionalText(payload.sourceLabel)
  const checkTime = normalizeOptionalText(payload.checkTime)
  if (!sourceLabel || !checkTime) {
    throw new Error('更新动态待确认')
  }

  const rawItems = Array.isArray(payload.items) ? payload.items : []
  const items = rawItems
    .map(item => normalizeUpdateHistoryItem(item))
    .filter((item): item is UpdateHistoryItem => item !== null)

  return {
    sourceType,
    sourceLabel,
    checkTime,
    repositoryUrl: normalizeOptionalText(payload.repositoryUrl),
    total: typeof payload.total === 'number' && payload.total >= 0 ? payload.total : items.length,
    items,
    error: normalizeOptionalText(payload.error)
  }
}

function normalizeUpdateHistoryItem(payload: unknown): UpdateHistoryItem | null {
  if (!isPlainObject(payload)) {
    return null
  }
  return {
    sourceType: normalizeSourceType(payload.sourceType),
    version: normalizeOptionalText(payload.version),
    rawTag: normalizeOptionalText(payload.rawTag),
    name: normalizeOptionalText(payload.name),
    publishedAt: normalizeOptionalText(payload.publishedAt),
    url: normalizeOptionalText(payload.url),
    notes: normalizeOptionalText(payload.notes),
    draft: payload.draft === true,
    prerelease: payload.prerelease === true,
    downloadUrl: normalizeOptionalText(payload.downloadUrl),
    refName: normalizeOptionalText(payload.refName),
    sha: normalizeOptionalText(payload.sha),
    shortSha: normalizeOptionalText(payload.shortSha),
    message: normalizeOptionalText(payload.message),
    author: normalizeOptionalText(payload.author),
    installed: payload.installed === true,
    latest: payload.latest === true
  }
}

onMounted(() => {
  void loadVersion()
  void checkUpdate()
  void loadUpdateHistory()
})
</script>

<style scoped>
.admin-update { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-muted); margin: 0; }
.glass-card { background: var(--card-color) !important; backdrop-filter: blur(16px); border: 1px solid var(--border-color) !important; border-radius: 16px !important; }
.check-placeholder { text-align: center; padding: 20px; color: var(--text-muted); display: flex; flex-direction: column; align-items: center; gap: 16px; }
.status-note { font-size: 12px; color: #ef4444; }
.update-result { display: flex; flex-direction: column; gap: 8px; }
.notes-text { font-size: 12px; line-height: 1.7; margin-bottom: 10px; white-space: pre-wrap; }
.action-row { display: flex; flex-wrap: wrap; gap: 8px; }
.link-text { color: #10b981; text-decoration: none; }
.link-text:hover { text-decoration: underline; }
.mono { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
.history-wrap { display: flex; flex-direction: column; gap: 14px; }
.history-summary { display: flex; flex-wrap: wrap; gap: 16px; font-size: 12px; color: var(--text-muted); }
.history-list { display: flex; flex-direction: column; gap: 12px; }
.history-item { border: 1px solid var(--border-color); border-radius: 14px; padding: 14px; background: color-mix(in srgb, var(--card-color) 88%, transparent); }
.history-item-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 8px; }
.history-title-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.history-title { font-size: 14px; color: var(--text-primary); }
.history-meta { display: flex; flex-wrap: wrap; gap: 16px; font-size: 12px; color: var(--text-muted); margin-bottom: 8px; }
.history-subtitle { font-size: 13px; font-weight: 600; color: var(--text-secondary); margin-bottom: 6px; }
.history-body { margin-bottom: 0; color: var(--text-secondary); }
</style>
