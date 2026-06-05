import request from './request'

export interface GenerationMetaOption {
  label: string
  value: string
}

export interface GenerationMetaVO {
  image?: {
    resolutionOptions?: GenerationMetaOption[]
    qualityOptions?: GenerationMetaOption[]
    defaults?: {
      resolution?: string
      quality?: string
    }
  }
  video?: {
    durationOptions?: GenerationMetaOption[]
    cameraMotionOptions?: GenerationMetaOption[]
    defaults?: {
      duration?: string
      cameraMotion?: string
    }
  }
}

export interface ImageGenerateOptions {
  modelName?: string
  aspectRatio?: string
  aspectWidth?: number
  aspectHeight?: number
  resolution?: string
  quality?: string
  width?: number
  height?: number
  cfg?: number
  steps?: number
}

export interface ImageGenerateDTO {
  projectId: number
  providerId: number
  prompt: string
  modelName?: string
  negativePrompt?: string
  referenceAssetIds?: number[]
  maskAssetId?: number
  size?: string
  count?: number
  options?: ImageGenerateOptions
}

export interface VideoGenerateDTO {
  projectId: number
  providerId: number
  prompt: string
  modelName?: string
  sourceAssetId?: number
  endAssetId?: number
  duration?: string
  options?: any
}

export const generationApi = {
  async getMeta() {
    return request.get('/api/generation/meta')
  },

  // 生图触发
  async generateImage(dto: ImageGenerateDTO) {
    return request.post('/api/generation/image', dto)
  },

  // 生视频触发
  async generateVideo(dto: VideoGenerateDTO) {
    return request.post('/api/generation/video', dto)
  },

  // 局部重绘触发
  async generateInpaint(dto: ImageGenerateDTO) {
    return request.post('/api/generation/image/inpaint', dto)
  }
}
