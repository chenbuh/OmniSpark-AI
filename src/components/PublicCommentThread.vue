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
  currentUserId.value = info.id || null
} catch {}

async function loadComments() {
  if (!commentEndpoint.value) {
    comments.value = []
    emit('countChange', 0)
    return
  }
  loading.value = true
  try {
    const res = await request.get(commentEndpoint.value)
    if (!Array.isArray((res as any).data)) {
      throw new Error('评论数据待确认')
    }
    const loadedComments = (res as any).data as PublicComment[]
    comments.value = loadedComments
    emit('countChange', totalComments(loadedComments))
  } catch (err: any) {
    comments.value = null
    message.error(err.message || '加载评论失败')
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

function totalComments(items: PublicComment[]) {
  return items.reduce((sum, item) => sum + 1 + (item.replies?.length || 0), 0)
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
    await request.post(commentEndpoint.value, {
      parentId,
      content
    })
    if (parentId) {
      cancelReply()
    } else {
      draft.value = ''
    }
    await loadComments()
    emit('submitted')
  } catch (err: any) {
    message.error(err.message || '评论失败')
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
    await request.delete(`${commentEndpoint.value}/${comment.id}`)
    if (replyTargetId.value === comment.id || activeReplyRootId.value === comment.id || comment.parentId === activeReplyRootId.value) {
      cancelReply()
    }
    await loadComments()
    message.success('评论已删除')
  } catch (err: any) {
    message.error(err.message || '删除评论失败')
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
