<template>
  <div class="comment-thread">
    <div class="composer">
      <n-input
        v-model:value="draft"
        type="textarea"
        placeholder="说点什么..."
        :autosize="{ minRows: 2, maxRows: 4 }"
      />
      <div class="composer-actions">
        <span class="hint">公开评论，所有登录用户可见</span>
        <n-button type="primary" size="small" :loading="submitting" @click="submitComment()">
          评论
        </n-button>
      </div>
    </div>

    <div v-if="loading && comments === null" class="empty-state">
      <n-spin size="small" />
    </div>

    <div class="comment-list" v-else-if="comments && comments.length > 0">
      <div v-for="comment in comments" :key="comment.id" class="comment-card">
        <div class="comment-head">
          <div class="author">
            <n-avatar round size="small" :src="comment.avatar || undefined">
              {{ authorInitial(comment) }}
            </n-avatar>
            <div class="meta">
              <span class="name">{{ authorName(comment) }}</span>
              <span class="time">{{ formatTime(comment.createdAt) }}</span>
            </div>
          </div>
          <n-space :size="4">
            <n-button text size="tiny" @click="startReply(comment)">回复</n-button>
            <n-button
              v-if="canDelete(comment)"
              text
              size="tiny"
              type="error"
              :loading="deletingId === comment.id"
              @click="removeComment(comment)"
            >
              删除
            </n-button>
          </n-space>
        </div>
        <div class="comment-content">{{ comment.content }}</div>

        <div v-if="activeReplyRootId === comment.id" class="reply-box">
          <n-input
            v-model:value="replyDraft"
            type="textarea"
            :placeholder="replyTargetLabel ? `回复 ${replyTargetLabel}` : `回复 ${authorName(comment)}`"
            :autosize="{ minRows: 2, maxRows: 3 }"
          />
          <div class="reply-actions">
            <n-button size="small" quaternary @click="cancelReply">取消</n-button>
            <n-button
              size="small"
              type="primary"
              :loading="submitting"
              @click="submitComment(replyTargetId || comment.id)"
            >
              回复
            </n-button>
          </div>
        </div>

        <div v-if="comment.replies?.length" class="reply-list">
          <div v-for="reply in comment.replies" :key="reply.id" class="reply-item">
            <div class="reply-head">
              <div class="author">
                <n-avatar round size="small" :src="reply.avatar || undefined">
                  {{ authorInitial(reply) }}
                </n-avatar>
                <div class="meta">
                  <span class="name">
                    {{ authorName(reply) }}
                    <template v-if="reply.replyToNickname || reply.replyToUsername">
                      <span class="reply-to"> 回复 {{ reply.replyToNickname || reply.replyToUsername }}</span>
                    </template>
                  </span>
                  <span class="time">{{ formatTime(reply.createdAt) }}</span>
                </div>
              </div>
              <n-space :size="4">
                <n-button text size="tiny" @click="startReply(reply)">回复</n-button>
                <n-button
                  v-if="canDelete(reply)"
                  text
                  size="tiny"
                  type="error"
                  :loading="deletingId === reply.id"
                  @click="removeComment(reply)"
                >
                  删除
                </n-button>
              </n-space>
            </div>
            <div class="comment-content">{{ reply.content }}</div>
          </div>
        </div>
      </div>
    </div>

    <n-empty v-else-if="comments !== null" description="还没有评论，来抢个沙发吧。" class="empty-state" />
    <n-empty v-else description="评论数据待确认，请稍后重试。" class="empty-state" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import request from '@/api/request'

interface PublicComment {
  id: number
  parentId?: number | null
  userId: number
  username?: string
  nickname?: string
  avatar?: string
  replyToUsername?: string
  replyToNickname?: string
  content: string
  createdAt: string
  replies?: PublicComment[]
}

const props = defineProps<{
  resourcePath: string
  resourceId?: number | null
}>()
const emit = defineEmits<{
  submitted: []
  countChange: [count: number]
}>()

const message = useMessage()
const comments = ref<PublicComment[] | null>(null)
const loading = ref(false)
const submitting = ref(false)
const deletingId = ref<number | null>(null)
const draft = ref('')
const replyDraft = ref('')
const activeReplyRootId = ref<number | null>(null)
const replyTargetId = ref<number | null>(null)
const replyTargetLabel = ref('')
const currentUserId = ref<number | null>(null)

const commentEndpoint = computed(() => {
  if (!props.resourceId) {
    return ''
  }
  return `${props.resourcePath}/${props.resourceId}/comments`
})

watch(commentEndpoint, async () => {
  cancelReply()
  await loadComments()
}, { immediate: true })

try {
  const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
  currentUserId.value = toPositiveNumberOrNull(info?.id)
} catch {}

function requireCreatedCommentResult(value: unknown) {
  if (!isPlainObject(value)) {
    throw new Error('评论结果待确认')
  }
  const id = requirePositiveNumber(value.id, '评论结果待确认')
  const content = normalizeRequiredText(value.content, '评论结果待确认')
  if (!content) {
    throw new Error('评论结果待确认')
  }
  return {
    id,
    content
  }
}

function requireDeletedCommentCount(value: unknown) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error('评论删除结果待确认')
  }
  return parsed
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string): unknown {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return fallback
}

function normalizeOptionalText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeRequiredText(value: unknown, errorMessage: string) {
  const normalized = normalizeOptionalText(value)
  if (!normalized) {
    throw new Error(errorMessage)
  }
  return normalized
}

function toPositiveNumberOrNull(value: unknown) {
  if (value == null || value === '') {
    return null
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

function requirePositiveNumber(value: unknown, errorMessage: string) {
  const parsed = toPositiveNumberOrNull(value)
  if (parsed == null) {
    throw new Error(errorMessage)
  }
  return parsed
}

function normalizeParentId(value: unknown) {
  if (value == null || value === '' || value === 0 || value === '0') {
    return null
  }
  return requirePositiveNumber(value, '评论数据待确认')
}

function normalizeCreatedAt(value: unknown) {
  return normalizeRequiredText(value, '评论数据待确认').replace('T', ' ').substring(0, 19)
}

function normalizeCommentTree(
  value: unknown,
  seenIds: Set<number>,
  errorMessage: string
): PublicComment {
  if (!isPlainObject(value)) {
    throw new Error(errorMessage)
  }
  const id = requirePositiveNumber(value.id, errorMessage)
  if (seenIds.has(id)) {
    throw new Error(errorMessage)
  }
  seenIds.add(id)
  const userId = requirePositiveNumber(value.userId, errorMessage)
  const content = normalizeRequiredText(value.content, errorMessage)
  const repliesValue = value.replies
  const replies = repliesValue == null
    ? undefined
    : requireCommentList(repliesValue, seenIds, errorMessage)

  return {
    id,
    parentId: normalizeParentId(value.parentId),
    userId,
    username: normalizeOptionalText(value.username) || undefined,
    nickname: normalizeOptionalText(value.nickname) || undefined,
    avatar: normalizeOptionalText(value.avatar) || undefined,
    replyToUsername: normalizeOptionalText(value.replyToUsername) || undefined,
    replyToNickname: normalizeOptionalText(value.replyToNickname) || undefined,
    content,
    createdAt: normalizeCreatedAt(value.createdAt),
    replies
  }
}

function requireCommentList(
  value: unknown,
  seenIds = new Set<number>(),
  errorMessage = '评论数据待确认'
): PublicComment[] {
  if (!Array.isArray(value)) {
    throw new Error(errorMessage)
  }
  return value.map(item => normalizeCommentTree(item, seenIds, errorMessage))
}

async function loadComments(silent = false): Promise<PublicComment[] | null> {
  if (!commentEndpoint.value) {
    comments.value = []
    emit('countChange', 0)
    return comments.value
  }
  loading.value = true
  try {
    const res = await request.get(commentEndpoint.value)
    const loadedComments = requireCommentList(getResponseData(res, '评论数据待确认'))
    comments.value = loadedComments
    emit('countChange', totalComments(loadedComments))
    return loadedComments
  } catch (err: unknown) {
    comments.value = null
    if (!silent) {
      message.error(getErrorMessage(err, '加载评论失败'))
    }
    return null
  } finally {
    loading.value = false
  }
}

function rawAuthorName(comment: PublicComment) {
  return comment.nickname?.trim() || comment.username?.trim() || ''
}

function authorName(comment: PublicComment) {
  return rawAuthorName(comment) || '未知作者'
}

function authorInitial(comment: PublicComment) {
  return rawAuthorName(comment).slice(0, 1).toUpperCase() || '?'
}

function formatTime(value?: string) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function totalComments(items: PublicComment[]): number {
  return items.reduce((sum, item) => sum + 1 + totalComments(item.replies || []), 0)
}

function findCommentById(items: PublicComment[], id: number): PublicComment | null {
  for (const item of items) {
    if (item.id === id) {
      return item
    }
    if (item.replies?.length) {
      const reply = findCommentById(item.replies, id)
      if (reply) {
        return reply
      }
    }
  }
  return null
}

function canDelete(comment: PublicComment) {
  return !!currentUserId.value && comment.userId === currentUserId.value
}

function startReply(comment: PublicComment) {
  const rawName = rawAuthorName(comment)
  activeReplyRootId.value = comment.parentId || comment.id
  replyTargetId.value = comment.id
  replyTargetLabel.value = authorName(comment)
  replyDraft.value = rawName ? `@${rawName} ` : ''
}

function cancelReply() {
  activeReplyRootId.value = null
  replyTargetId.value = null
  replyTargetLabel.value = ''
  replyDraft.value = ''
}

async function submitComment(parentId?: number) {
  const content = parentId ? replyDraft.value.trim() : draft.value.trim()
  if (!content) {
    message.error('评论内容不能为空')
    return
  }
  if (!commentEndpoint.value) {
    return
  }
  submitting.value = true
  try {
    const previousCount = comments.value ? totalComments(comments.value) : null
    const res = await request.post(commentEndpoint.value, {
      parentId,
      content
    })
    const created = requireCreatedCommentResult(getResponseData(res, '评论结果待确认'))
    const refreshedComments = await loadComments(true)
    if (!refreshedComments) {
      throw new Error('评论结果待确认')
    }
    const confirmedComment = findCommentById(refreshedComments, created.id)
    if (!confirmedComment || confirmedComment.content.trim() !== created.content) {
      throw new Error('评论结果待确认')
    }
    if (previousCount !== null && totalComments(refreshedComments) < previousCount + 1) {
      throw new Error('评论结果待确认')
    }
    if (parentId) {
      cancelReply()
    } else {
      draft.value = ''
    }
    emit('submitted')
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '评论失败'))
  } finally {
    submitting.value = false
  }
}

async function removeComment(comment: PublicComment) {
  if (!commentEndpoint.value) {
    return
  }
  deletingId.value = comment.id
  try {
    const res = await request.delete(`${commentEndpoint.value}/${comment.id}`)
    requireDeletedCommentCount(getResponseData(res, '评论删除结果待确认'))
    if (replyTargetId.value === comment.id || activeReplyRootId.value === comment.id || comment.parentId === activeReplyRootId.value) {
      cancelReply()
    }
    const refreshedComments = await loadComments(true)
    if (!refreshedComments) {
      throw new Error('评论删除结果待确认')
    }
    const stillExists = !!findCommentById(refreshedComments, comment.id)
    if (stillExists) {
      throw new Error('评论删除结果待确认')
    }
    message.success('评论已删除')
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '删除评论失败'))
  } finally {
    deletingId.value = null
  }
}
</script>

<style scoped>
.comment-thread {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.composer,
.comment-card,
.reply-item {
  border: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.03);
  border-radius: 12px;
}

.composer {
  padding: 12px;
}

.composer-actions,
.reply-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
}

.hint {
  font-size: 12px;
  color: var(--text-muted);
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment-card {
  padding: 12px;
}

.comment-head,
.reply-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.author {
  display: flex;
  align-items: center;
  gap: 10px;
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.reply-to {
  color: var(--text-muted);
  font-weight: 400;
}

.time {
  font-size: 11px;
  color: var(--text-muted);
}

.comment-content {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.reply-box {
  margin-top: 12px;
}

.reply-list {
  margin-top: 12px;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-left: 1px solid var(--border-light);
}

.reply-item {
  padding: 10px;
}

.empty-state {
  padding: 24px 0;
}
</style>
