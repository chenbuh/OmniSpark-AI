<template>
  <div class="community-container">
    <div class="page-header">
      <h2>社区提示词共享 (Community)</h2>
      <p class="subtitle">浏览其他创作者的提示词与效果图，或分享您的作品给社区。</p>
    </div>

    <!-- 分类 + 搜索 + 发布 -->
    <n-card class="glass-card filter-card" :bordered="false">
      <div class="filter-bar">
        <n-tabs v-model:value="activeCategory" type="segment" class="category-tabs" @update:value="onCategoryChange">
          <n-tab name="all">全部</n-tab>
          <n-tab
            v-for="category in categoryTabs"
            :key="category.value"
            :name="category.value"
          >
            {{ category.label }}
          </n-tab>
        </n-tabs>
        <n-space>
          <n-select v-model:value="sortBy" :options="sortOptions" style="width:130px;" size="small" @update:value="onCategoryChange" />
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
    <div class="posts-grid" v-else-if="posts && posts.length > 0">
      <div v-for="post in posts" :key="post.id" class="post-card glass-card">
        <div class="post-image" v-if="post.imageUrl" @click="showDetail(post)">
          <img :src="post.imageUrl" class="post-img" loading="lazy" />
        </div>
        <div class="post-image placeholder-img" v-else @click="showDetail(post)">
          <Image class="ph-icon" />
        </div>
        <div class="post-body">
          <div class="post-head">
            <span class="post-title" @click="showDetail(post)">{{ post.title }}</span>
            <n-tag v-if="post.category" size="tiny" round>{{ post.category }}</n-tag>
          </div>
          <n-ellipsis :line-clamp="2" class="post-prompt" @click="showDetail(post)">{{ post.prompt }}</n-ellipsis>
          <div class="post-meta">
            <span class="post-author">👤 {{ authorLabel(post) }}</span>
            <span class="post-model" v-if="post.modelName"><code>{{ post.modelName }}</code></span>
          </div>
          <div class="post-actions">
            <n-button size="tiny" quaternary @click="handleLike(post)" :type="post.liked ? 'primary' : 'default'">
              <template #icon><ThumbsUp /></template>{{ formatInteractionCount(post.likesCount) }}
            </n-button>
            <n-button size="tiny" quaternary @click="showDetail(post)">
              <template #icon><MessageCircle /></template>{{ formatInteractionCount(post.commentsCount) }}
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

    <n-empty
      v-else-if="posts !== null"
      description="暂无社区内容，成为第一个分享者！"
      style="padding: 60px 0;"
    />
    <n-empty v-else description="社区内容待确认，请稍后重试。" style="padding: 60px 0;" />

    <div class="pager" v-if="(total ?? 0) > 0">
      <n-pagination
        v-model:page="page"
        :page-size="pageSize"
        :item-count="total"
        show-size-picker
        :page-sizes="pageSizeOptions"
        @update:page="loadPosts"
        @update:page-size="handlePageSizeChange"
      />
    </div>

    <!-- 发布弹窗 -->
    <n-modal v-model:show="showUploadModal" preset="card" title="分享到社区" style="width: 540px;" closable>
      <n-form :model="form" label-placement="top">
        <n-form-item label="标题" required>
          <n-input v-model:value="form.title" placeholder="给您的作品取个名字..." :maxlength="80" show-count />
        </n-form-item>
        <n-row :gutter="12">
          <n-col :span="12">
            <n-form-item label="分类">
              <n-select
                v-model:value="form.category"
                :options="categoryOptions"
                filterable
                tag
                placeholder="选择或输入一个分类"
              />
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
              <n-button size="small" secondary @click="openAssetPicker">
                <template #icon><FolderOpen /></template>从资产库选择
              </n-button>
              <n-button size="small" secondary :loading="uploadingImage" @click="triggerImageUpload">
                {{ form.imageUrl ? '重新上传' : '上传本地' }}
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

    <!-- 从共享资产库选择效果图 -->
    <n-modal
      v-model:show="showAssetPicker"
      preset="card"
      title="从共享资产库选择效果图"
      style="width: 60vw; max-width: 800px;"
    >
      <div class="assets-picker-grid">
        <div
          v-for="asset in imageAssets"
          :key="asset.id"
          class="picker-item"
          @click="handleSelectAsset(asset)"
        >
          <img :src="asset.thumbUrl" :alt="asset.fileName || '可选资产缩略图'" class="picker-thumb" />
          <div class="picker-info">
            <span class="picker-name">{{ asset.fileName }}</span>
          </div>
        </div>
        <div v-if="imageAssets.length === 0" class="picker-empty">
          资产库中尚无图片，可先上传本地图片或前往生图页生成。
        </div>
      </div>
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
            <span><n-tag size="small">👤 {{ authorLabel(detailPost) }}</n-tag></span>
            <span v-if="detailPost.category"><n-tag size="small">🏷 {{ detailPost.category }}</n-tag></span>
            <span v-if="detailPost.modelName"><n-tag size="small">🤖 {{ detailPost.modelName }}</n-tag></span>
            <span><n-tag size="small">❤️ {{ formatInteractionCount(detailPost.likesCount) }}</n-tag></span>
            <span><n-tag size="small">💬 {{ formatInteractionCount(detailPost.commentsCount) }}</n-tag></span>
            <span><n-tag size="small">🕐 {{ String(detailPost.createdAt||'').replace('T',' ').substring(5,16) }}</n-tag></span>
          </div>
          <PublicCommentThread
            resource-path="/api/community/posts"
            :resource-id="detailPost.id"
            @count-change="handleCommunityCommentCountChange"
          />
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
import { Search, Upload, ThumbsUp, Zap, Image, Trash2, Edit3, FolderOpen, MessageCircle } from 'lucide-vue-next'
import SkeletonCard from '@/components/SkeletonCard.vue'
import PublicCommentThread from '@/components/PublicCommentThread.vue'
import request from '@/api/request'
import { assetApi } from '@/api/assets'
import { useProjectStore } from '@/store/project'
import { useAssetStore, resolveAssetUrl, type Asset } from '@/store/asset'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const projectStore = useProjectStore()
const assetStore = useAssetStore()

const loading = ref(true)
const publishing = ref(false)
const uploadingImage = ref(false)
const posts = ref<any[] | null>(null)
const activeCategory = ref('all')
const sortBy = ref('newest')
const searchQuery = ref('')
const categories = ref<string[]>([])
const showUploadModal = ref(false)
const showDetailDrawer = ref(false)
const detailPost = ref<any>(null)
const imageUploadInput = ref<HTMLInputElement | null>(null)
const editingPostId = ref<number | null>(null)
const showAssetPicker = ref(false)
const page = ref(1)
const pageSize = ref(20)
const pageSizeOptions = [12, 20, 40, 80]
const total = ref<number | null>(null)

const currentUserId = ref<number | null>(null)

// 共享资产库中的图片资产
const imageAssets = computed(() => {
  return assetStore
    .getAssetsByProject(projectStore.activeProjectId)
    .filter(a => a.assetType === 'image' || a.assetType === 'reference')
})

const sortOptions = [
  { label: '最新发布', value: 'newest' },
  { label: '最多点赞', value: 'likes' },
  { label: '最多评论', value: 'comments' }
]

const form = reactive({
  title: '', prompt: '', negativePrompt: '', modelName: '',
  imageUrl: '', category: '', tags: ''
})

const imagePreviewUrl = computed(() => resolveAssetUrl(form.imageUrl))

const categoryTabs = computed(() => {
  return categories.value.map(item => ({
    label: item,
    value: item
  }))
})

const resetPublishForm = () => {
  Object.assign(form, { title: '', prompt: '', negativePrompt: '', modelName: '', imageUrl: '', category: '', tags: '' })
  clearUploadedImage()
  editingPostId.value = null
}

const categoryOptions = computed(() => {
  const values = new Set<string>(categories.value)
  if (form.category?.trim()) {
    values.add(form.category.trim())
  }
  return Array.from(values).map(item => ({
    label: item,
    value: item
  }))
})

function canDelete(post: any) {
  return post.userId && currentUserId.value && post.userId === currentUserId.value
}

function authorLabel(post: any) {
  return post?.nickname?.trim() || post?.username?.trim() || '未知作者'
}

function formatInteractionCount(count: number | null | undefined) {
  return typeof count === 'number' ? count : '-'
}

function updateKnownCount(count: number | null | undefined, delta: number) {
  return typeof count === 'number' ? Math.max(0, count + delta) : count
}

let searchTimer: any = null
function debounceSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; loadPosts() }, 300)
}

// 分类切换时回到第 1 页并重新请求
function onCategoryChange() {
  page.value = 1
  loadPosts()
}

// 切换每页条数时回到第 1 页并重新请求
function handlePageSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  loadPosts()
}

async function loadPosts() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: page.value,
      pageSize: pageSize.value,
      sort: sortBy.value
    }
    if (activeCategory.value !== 'all') params.category = activeCategory.value
    if (searchQuery.value) params.search = searchQuery.value
    const res = await request.get('/api/community/posts', { params })
    const data = (res as any).data || {}
    if (!Array.isArray(data.records)) {
      posts.value = null
      total.value = null
      return
    }
    posts.value = data.records.map((post: any) => ({
      ...post,
      imageUrl: resolveAssetUrl(post.imageUrl)
    }))
    total.value = typeof data.total === 'number' ? data.total : 0
  } catch (err: any) {
    posts.value = null
    total.value = null
    message.error(err.message || '加载社区内容失败')
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    const res = await request.get('/api/community/categories')
    const values = Array.isArray((res as any).data) ? (res as any).data : []
    categories.value = values
      .map((item: unknown) => typeof item === 'string' ? item.trim() : '')
      .filter((item: string) => !!item)
    if (activeCategory.value !== 'all' && !categories.value.includes(activeCategory.value)) {
      activeCategory.value = 'all'
    }
  } catch {
    categories.value = []
  }
}

onMounted(async () => {
  // 从 localStorage 获取当前用户 ID
  try {
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
    currentUserId.value = info.id || null
  } catch {}

  if (route.query.sharePrompt) {
    form.prompt = route.query.sharePrompt as string
    form.modelName = (route.query.shareModel as string) || ''
    form.imageUrl = toRelativeUrl((route.query.shareImage as string) || '')
    showUploadModal.value = true
  }
  await loadCategories()
  await loadPosts()
})

function triggerImageUpload() {
  imageUploadInput.value?.click()
}

// 打开共享资产库选择器
async function openAssetPicker() {
  if (!projectStore.activeProjectId) {
    message.error('请先选择一个项目空间')
    return
  }
  showAssetPicker.value = true
  try {
    await assetStore.refresh({ projectId: projectStore.activeProjectId })
  } catch { /* 忽略，选择器会显示空态 */ }
}

// 选中资产库图片作为效果图（保存相对路径，避免写死后端 origin）
function handleSelectAsset(asset: Asset) {
  form.imageUrl = toRelativeUrl(asset.fileUrl)
  showAssetPicker.value = false
}

// 把可能为绝对地址的资源 URL 转成相对路径（/uploads/...），跨源部署仍可用
function toRelativeUrl(url: string): string {
  if (!url) return ''
  try {
    return new URL(url, window.location.origin).pathname
  } catch {
    return url.split('?')[0]?.split('#')[0] || url
  }
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
    form.imageUrl = toRelativeUrl(res.data?.fileUrl || res.data?.thumbUrl || '')
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
    const payload = {
      ...form,
      category: form.category?.trim() || undefined
    }
    if (editingPostId.value) {
      await request.put(`/api/community/posts/${editingPostId.value}`, payload)
      message.success('已更新！')
    } else {
      await request.post('/api/community/posts', payload)
      message.success('发布成功！')
    }
    showUploadModal.value = false
    resetPublishForm()
    await Promise.all([loadCategories(), loadPosts()])
  } catch (err: any) { message.error(err.message || '发布失败') }
  finally { publishing.value = false }
}

function handleEditPost(post: any) {
  editingPostId.value = post.id
  form.title = post.title
  form.prompt = post.prompt
  form.negativePrompt = post.negativePrompt || ''
  form.modelName = post.modelName || ''
  form.imageUrl = toRelativeUrl(post.imageUrl || '')
  form.category = post.category || ''
  form.tags = post.tags || ''
  showUploadModal.value = true
}

async function handleLike(post: any) {
  try {
    const res = await request.post(`/api/community/posts/${post.id}/like`)
    const liked = (res as any).data
    post.liked = liked
    post.likesCount = updateKnownCount(post.likesCount, liked ? 1 : -1)
  } catch { message.error('操作失败，请先登录') }
}

async function handleDelete(id: number) {
  try {
    await request.delete(`/api/community/posts/${id}`)
    await loadCategories()
    await loadPosts()
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

function handleCommunityCommentCountChange(count: number) {
  if (!detailPost.value) return
  detailPost.value.commentsCount = count
  const target = posts.value?.find((item: any) => item.id === detailPost.value?.id)
  if (target) {
    target.commentsCount = count
  }
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
.pager { display: flex; justify-content: center; margin-top: 20px; }

/* 资产选择网格 */
.assets-picker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
  max-height: 60vh;
  overflow-y: auto;
}
.picker-item {
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid var(--border-color);
  transition: transform .15s, border-color .15s;
}
.picker-item:hover {
  transform: translateY(-2px);
  border-color: rgba(16, 185, 129, 0.6);
}
.picker-thumb {
  width: 100%;
  height: 100px;
  object-fit: cover;
  display: block;
}
.picker-info { padding: 6px 8px; }
.picker-name {
  font-size: 11px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}
.picker-empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 40px 20px;
  color: var(--text-muted);
  font-size: 13px;
}
</style>
