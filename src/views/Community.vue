<template>
  <div class="community-container">
    <div class="page-header">
      <h2>社区提示词共享 (Community)</h2>
      <p class="subtitle">浏览其他创作者的提示词与效果图，或分享您的作品给社区。</p>
    </div>

    <!-- 分类 + 搜索 + 发布 -->
    <n-card class="glass-card filter-card" :bordered="false">
      <div class="filter-bar">
        <n-tabs v-model:value="activeCategory" type="segment" class="category-tabs">
          <n-tab name="all">全部</n-tab>
          <n-tab name="写实">写实</n-tab>
          <n-tab name="动漫">动漫</n-tab>
          <n-tab name="赛博朋克">赛博朋克</n-tab>
          <n-tab name="科幻">科幻</n-tab>
          <n-tab name="3D">3D</n-tab>
          <n-tab name="插画">插画</n-tab>
          <n-tab name="uncategorized">其他</n-tab>
        </n-tabs>
        <n-space>
          <n-select v-model:value="sortBy" :options="sortOptions" style="width:130px;" size="small" @update:value="loadPosts" />
          <n-input v-model:value="searchQuery" placeholder="搜索提示词..." style="width:200px;" clearable @update:value="debounceSearch">
            <template #prefix><Search class="s-icon" /></template>
          </n-input>
          <n-button type="primary" @click="showUploadModal = true; editingPostId = null; resetPublishForm()">
            <template #icon><Upload /></template>发布
          </n-button>
        </n-space>
      </div>
    </n-card>

    <!-- 加载骨架 -->
    <SkeletonCard v-if="loading" type="grid" :count="8" />

    <!-- 瀑布流 -->
    <div class="posts-grid" v-else-if="filteredPosts.length > 0">
      <div v-for="post in filteredPosts" :key="post.id" class="post-card glass-card">
        <div class="post-image" v-if="post.imageUrl" @click="showDetail(post)">
          <img :src="post.imageUrl" class="post-img" loading="lazy" />
        </div>
        <div class="post-image placeholder-img" v-else @click="showDetail(post)">
          <Image class="ph-icon" />
        </div>
        <div class="post-body">
          <div class="post-head">
            <span class="post-title" @click="showDetail(post)">{{ post.title }}</span>
            <n-tag v-if="post.category && post.category !== 'uncategorized'" size="tiny" round>{{ post.category }}</n-tag>
          </div>
          <n-ellipsis :line-clamp="2" class="post-prompt" @click="showDetail(post)">{{ post.prompt }}</n-ellipsis>
          <div class="post-meta">
            <span class="post-author">👤 {{ post.username || '匿名' }}</span>
            <span class="post-model" v-if="post.modelName"><code>{{ post.modelName }}</code></span>
          </div>
          <div class="post-actions">
            <n-button size="tiny" quaternary @click="handleLike(post)" :type="post.liked ? 'primary' : 'default'">
              <template #icon><ThumbsUp /></template>{{ post.likesCount || 0 }}
            </n-button>
            <n-button size="tiny" secondary @click="handleApply(post)">
              <template #icon><Zap /></template>复用
            </n-button>
            <n-button v-if="canDelete(post)" size="tiny" quaternary @click="handleEditPost(post)">
              <template #icon><Edit3 /></template>
            </n-button>
            <n-button v-if="canDelete(post)" size="tiny" type="error" tertiary @click="handleDelete(post.id)">
              <template #icon><Trash2 /></template>
            </n-button>
          </div>
        </div>
      </div>
    </div>

    <div class="load-more-wrap" v-if="filteredPosts.length > 0 && hasMore">
      <n-button size="small" secondary @click="loadMorePosts" :loading="loading">加载更多</n-button>
    </div>

    <n-empty v-else description="暂无社区内容，成为第一个分享者！" style="padding: 60px 0;" />

    <!-- 发布弹窗 -->
    <n-modal v-model:show="showUploadModal" preset="card" title="分享到社区" style="width: 540px;" closable>
      <n-form :model="form" label-placement="top">
        <n-form-item label="标题" required>
          <n-input v-model:value="form.title" placeholder="给您的作品取个名字..." :maxlength="80" show-count />
        </n-form-item>
        <n-row :gutter="12">
          <n-col :span="12">
            <n-form-item label="分类">
              <n-select v-model:value="form.category" :options="categoryOptions" />
            </n-form-item>
          </n-col>
          <n-col :span="12">
            <n-form-item label="模型名称">
              <n-input v-model:value="form.modelName" placeholder="如 dall-e-3" />
            </n-form-item>
          </n-col>
        </n-row>
        <n-form-item label="提示词 (Prompt)" required>
          <n-input v-model:value="form.prompt" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" placeholder="完整的提示词内容..." :maxlength="2000" show-count />
        </n-form-item>
        <n-form-item label="负向提示词">
          <n-input v-model:value="form.negativePrompt" placeholder="可选" :maxlength="500" />
        </n-form-item>
        <n-form-item label="效果图">
          <div class="upload-field">
            <input ref="imageUploadInput" type="file" class="hidden-input" accept="image/*" @change="handleImageUpload" />
            <div class="upload-panel" @click="triggerImageUpload">
              <div v-if="imagePreviewUrl" class="upload-preview">
                <img :src="imagePreviewUrl" class="preview-thumb" @error="form.imageUrl = ''" />
                <div class="preview-mask">
                  <span>点击重新上传</span>
                </div>
              </div>
              <div v-else class="upload-placeholder">
                <Upload class="upload-icon" />
                <span>上传一张效果图作为社区封面</span>
                <small>支持 JPG / PNG / WebP</small>
              </div>
            </div>
            <div class="upload-actions">
              <n-button size="small" secondary :loading="uploadingImage" @click="triggerImageUpload">
                {{ form.imageUrl ? '重新上传' : '选择图片' }}
              </n-button>
              <n-button v-if="form.imageUrl" size="small" quaternary @click="clearUploadedImage">
                移除
              </n-button>
            </div>
          </div>
        </n-form-item>
        <n-form-item label="标签">
          <n-input v-model:value="form.tags" placeholder="逗号分隔，如：科幻, 太空, 飞船" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-button type="primary" @click="handlePublish" :loading="publishing">发布到社区</n-button>
      </template>
    </n-modal>

    <!-- 详情抽屉 -->
    <n-drawer v-model:show="showDetailDrawer" :width="480" placement="right">
      <n-drawer-content :title="detailPost?.title" closable>
        <div class="detail-body" v-if="detailPost">
          <img v-if="detailPost.imageUrl" :src="detailPost.imageUrl" class="detail-img" />
          <div class="detail-section">
            <span class="dl-label">提示词</span>
            <p class="dl-text highlight">{{ detailPost.prompt }}</p>
          </div>
          <div class="detail-section" v-if="detailPost.negativePrompt">
            <span class="dl-label">负向提示词</span>
            <p class="dl-text">{{ detailPost.negativePrompt }}</p>
          </div>
          <div class="detail-info">
            <span><n-tag size="small">👤 {{ detailPost.username || '匿名' }}</n-tag></span>
            <span><n-tag size="small">🏷 {{ detailPost.category || '未分类' }}</n-tag></span>
            <span v-if="detailPost.modelName"><n-tag size="small">🤖 {{ detailPost.modelName }}</n-tag></span>
            <span><n-tag size="small">❤️ {{ detailPost.likesCount || 0 }}</n-tag></span>
            <span><n-tag size="small">🕐 {{ String(detailPost.createdAt||'').replace('T',' ').substring(5,16) }}</n-tag></span>
          </div>
          <div class="detail-actions">
            <n-button type="primary" block @click="handleApply(detailPost)">
              <Zap class="btn-icon" /> 复用此提示词生图
            </n-button>
            <n-button v-if="canDelete(detailPost)" type="error" tertiary block @click="handleDelete(detailPost.id); showDetailDrawer = false">
              <Trash2 class="btn-icon" /> 删除此发布
            </n-button>
          </div>
        </div>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { Search, Upload, ThumbsUp, Zap, Image, Trash2, Edit3 } from 'lucide-vue-next'
import SkeletonCard from '@/components/SkeletonCard.vue'
import request from '@/api/request'
import { assetApi } from '@/api/assets'
import { useProjectStore } from '@/store/project'
import { resolveAssetUrl } from '@/store/asset'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const projectStore = useProjectStore()

const loading = ref(true)
const publishing = ref(false)
const uploadingImage = ref(false)
const posts = ref<any[]>([])
const activeCategory = ref('all')
const sortBy = ref('newest')
const searchQuery = ref('')
const showUploadModal = ref(false)
const showDetailDrawer = ref(false)
const detailPost = ref<any>(null)
const imageUploadInput = ref<HTMLInputElement | null>(null)
const editingPostId = ref<number | null>(null)
const page = ref(1)
const hasMore = ref(true)

const currentUserId = ref<number | null>(null)

const sortOptions = [
  { label: '最新发布', value: 'newest' },
  { label: '最多点赞', value: 'likes' }
]

const form = reactive({
  title: '', prompt: '', negativePrompt: '', modelName: '',
  imageUrl: '', category: 'uncategorized', tags: ''
})

const imagePreviewUrl = computed(() => resolveAssetUrl(form.imageUrl))

const resetPublishForm = () => {
  Object.assign(form, { title: '', prompt: '', negativePrompt: '', modelName: '', imageUrl: '', category: 'uncategorized', tags: '' })
  clearUploadedImage()
  editingPostId.value = null
}

const categoryOptions = [
  { label: '写实', value: '写实' }, { label: '动漫', value: '动漫' },
  { label: '赛博朋克', value: '赛博朋克' }, { label: '科幻', value: '科幻' },
  { label: '3D渲染', value: '3D' }, { label: '插画', value: '插画' },
  { label: '其他', value: 'uncategorized' }
]

const filteredPosts = computed(() => {
  let list = posts.value
  if (activeCategory.value !== 'all') {
    list = list.filter((p: any) => p.category === activeCategory.value)
  }
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter((p: any) => p.title?.toLowerCase().includes(q) || p.prompt?.toLowerCase().includes(q))
  }
  return list
})

function canDelete(post: any) {
  return post.userId && currentUserId.value && post.userId === currentUserId.value
}

let searchTimer: any = null
function debounceSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadPosts, 300)
}

async function loadPosts(loadMore = false) {
  if (!loadMore) { loading.value = true; page.value = 1; hasMore.value = true }
  try {
    const params: Record<string, string> = {
      page: String(page.value),
      sort: sortBy.value
    }
    if (searchQuery.value) params.search = searchQuery.value
    const res = await request.get('/api/community/posts', { params })
    const newPosts = ((res as any).data?.list || (res as any).data || []).map((post: any) => ({
      ...post,
      imageUrl: resolveAssetUrl(post.imageUrl)
    }))
    if (loadMore) {
      posts.value = [...posts.value, ...newPosts]
    } else {
      posts.value = newPosts
    }
    hasMore.value = newPosts.length >= 20
  } catch { if (!loadMore) posts.value = [] }
  finally { if (!loadMore) loading.value = false }
}

function loadMorePosts() {
  page.value++
  loadPosts(true)
}

onMounted(() => {
  // 从 localStorage 获取当前用户 ID
  try {
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
    currentUserId.value = info.id || null
  } catch {}

  if (route.query.sharePrompt) {
    form.prompt = route.query.sharePrompt as string
    form.modelName = (route.query.shareModel as string) || ''
    form.imageUrl = (route.query.shareImage as string) || ''
    showUploadModal.value = true
  }
  loadPosts()
})

function triggerImageUpload() {
  imageUploadInput.value?.click()
}

function clearUploadedImage() {
  form.imageUrl = ''
  if (imageUploadInput.value) {
    imageUploadInput.value.value = ''
  }
}

async function handleImageUpload(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  if (!projectStore.activeProjectId) {
    message.error('请先选择一个项目空间')
    target.value = ''
    return
  }

  uploadingImage.value = true
  try {
    const formData = new FormData()
    formData.append('projectId', String(projectStore.activeProjectId))
    formData.append('file', file)
    const res = await assetApi.uploadAsset(formData)
    form.imageUrl = res.data?.fileUrl || res.data?.thumbUrl || ''
    message.success('效果图上传成功')
  } catch (err: any) {
    message.error(err.message || '图片上传失败')
  } finally {
    uploadingImage.value = false
    target.value = ''
  }
}

async function handlePublish() {
  if (!form.title || !form.prompt) { message.error('标题和提示词为必填'); return }
  publishing.value = true
  try {
    if (editingPostId.value) {
      await request.put(`/api/community/posts/${editingPostId.value}`, { ...form })
      message.success('已更新！')
    } else {
      await request.post('/api/community/posts', { ...form })
      message.success('发布成功！')
    }
    showUploadModal.value = false
    resetPublishForm()
    await loadPosts()
  } catch (err: any) { message.error(err.message || '发布失败') }
  finally { publishing.value = false }
}

function handleEditPost(post: any) {
  editingPostId.value = post.id
  form.title = post.title
  form.prompt = post.prompt
  form.negativePrompt = post.negativePrompt || ''
  form.modelName = post.modelName || ''
  form.imageUrl = post.imageUrl || ''
  form.category = post.category || 'uncategorized'
  form.tags = post.tags || ''
  showUploadModal.value = true
}

async function handleLike(post: any) {
  try {
    const res = await request.post(`/api/community/posts/${post.id}/like`)
    const liked = (res as any).data
    post.liked = liked
    post.likesCount = (post.likesCount || 0) + (liked ? 1 : -1)
  } catch { message.error('操作失败，请先登录') }
}

async function handleDelete(id: number) {
  try {
    await request.delete(`/api/community/posts/${id}`)
    posts.value = posts.value.filter((p: any) => p.id !== id)
    message.success('已删除')
  } catch { message.error('删除失败') }
}

function handleApply(post: any) {
  const params: Record<string, string> = { prompt: post.prompt }
  if (post.negativePrompt) params.negPrompt = post.negativePrompt
  if (post.modelName) params.model = post.modelName
  router.push({ path: '/generate/image', query: params })
  message.success(`已应用「${post.title}」的提示词`)
}

function showDetail(post: any) {
  detailPost.value = post
  showDetailDrawer.value = true
}
</script>

<style scoped>
.community-container { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-muted); margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid var(--border-color) !important; border-radius: 16px !important; }
.filter-bar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.category-tabs { max-width: 600px; }
.s-icon { width: 16px; height: 16px; color: var(--text-muted); }

.posts-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; margin-top: 20px; }
.post-card { display: flex; flex-direction: column; overflow: hidden; transition: transform .2s, box-shadow .2s; }
.post-card:hover { transform: translateY(-3px); box-shadow: 0 8px 30px rgba(0,0,0,0.25); }
.post-image { height: 160px; overflow: hidden; cursor: pointer; }
.post-img { width: 100%; height: 100%; object-fit: cover; transition: transform .3s; }
.post-card:hover .post-img { transform: scale(1.05); }
.placeholder-img { display: flex; align-items: center; justify-content: center; background: rgba(128,128,128,0.05); }
.ph-icon { width: 36px; height: 36px; opacity: 0.3; }
.post-body { padding: 12px 14px; flex: 1; display: flex; flex-direction: column; gap: 6px; }
.post-head { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.post-title { font-size: 14px; font-weight: 600; color: var(--text-primary); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.post-title:hover { color: #10b981; }
.post-prompt { font-size: 12px; color: var(--text-muted); line-height: 1.5; cursor: pointer; }
.post-meta { display: flex; align-items: center; gap: 8px; font-size: 11px; }
.post-author { color: var(--text-muted); }
.post-model code { color: var(--text-muted); font-size: 10px; }
.post-actions { display: flex; gap: 6px; padding-top: 6px; border-top: 1px solid var(--border-light); }

.detail-body { display: flex; flex-direction: column; gap: 16px; }
.detail-img { width: 100%; border-radius: 12px; }
.dl-label { font-size: 12px; color: var(--text-muted); font-weight: 600; }
.dl-text { font-size: 13px; color: var(--text-secondary); line-height: 1.6; margin: 4px 0 0; padding: 12px; background: rgba(128,128,128,0.06); border-radius: 8px; }
.dl-text.highlight { border-left: 3px solid #10b981; }
.detail-info { display: flex; flex-wrap: wrap; gap: 8px; }
.detail-actions { display: flex; flex-direction: column; gap: 8px; padding-top: 12px; }
.btn-icon { width: 14px; height: 14px; margin-right: 4px; }

.hidden-input { display: none; }
.upload-field { display: flex; flex-direction: column; gap: 10px; }
.upload-panel {
  min-height: 156px;
  border-radius: 12px;
  border: 1px dashed var(--border-color);
  background: rgba(255,255,255,0.03);
  cursor: pointer;
  overflow: hidden;
  transition: border-color .2s, background .2s;
}
.upload-panel:hover {
  border-color: rgba(16,185,129,0.5);
  background: rgba(16,185,129,0.04);
}
.upload-placeholder {
  min-height: 156px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 13px;
}
.upload-placeholder small {
  font-size: 11px;
  color: var(--text-muted);
}
.upload-icon { width: 24px; height: 24px; opacity: 0.75; }
.upload-preview {
  position: relative;
  width: 100%;
  height: 156px;
}
.preview-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.preview-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0,0,0,0.35);
  color: #fff;
  font-size: 12px;
  opacity: 0;
  transition: opacity .2s;
}
.upload-preview:hover .preview-mask { opacity: 1; }
.upload-actions { display: flex; gap: 8px; }
.load-more-wrap { display: flex; justify-content: center; padding: 24px 0; }
</style>
