export interface GenerationReuseSource {
  taskType: 'image' | 'video'
  prompt?: string
  negativePrompt?: string
  modelName?: string
  requestJson?: string
}

type ReuseOptions = {
  overrideSourceAssetId?: number | null
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function normalizeText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function toPositiveInteger(value: unknown) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return Math.round(parsed)
}

function toFiniteNumber(value: unknown) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function getRequestPayload(requestJson?: string) {
  if (!requestJson) {
    return null
  }
  try {
    const parsed = JSON.parse(requestJson)
    return isPlainObject(parsed) ? parsed : null
  } catch {
    return null
  }
}

function appendQuery(query: Record<string, string>, key: string, value: unknown) {
  const normalized = normalizeText(value)
  if (normalized) {
    query[key] = normalized
  }
}

function appendPositiveIntegerQuery(query: Record<string, string>, key: string, value: unknown) {
  const normalized = toPositiveInteger(value)
  if (normalized !== null) {
    query[key] = String(normalized)
  }
}

function appendFiniteNumberQuery(query: Record<string, string>, key: string, value: unknown) {
  const normalized = toFiniteNumber(value)
  if (normalized !== null) {
    query[key] = String(normalized)
  }
}

export function buildGenerationReuseLocation(source: GenerationReuseSource, options: ReuseOptions = {}) {
  const payload = getRequestPayload(source.requestJson)
  const payloadOptions = isPlainObject(payload?.options) ? payload.options : {}

  if (source.taskType === 'image') {
    const query: Record<string, string> = {}
    const resolution = normalizeText(payloadOptions.resolution)
    const aspectRatio = normalizeText(payloadOptions.aspectRatio)
    appendQuery(query, 'prompt', payload?.prompt ?? source.prompt)
    appendQuery(query, 'negPrompt', payload?.negativePrompt ?? source.negativePrompt)
    appendQuery(query, 'model', payload?.modelName ?? source.modelName)
    appendPositiveIntegerQuery(query, 'providerId', payload?.providerId)
    appendPositiveIntegerQuery(query, 'count', payload?.count)
    if (!resolution || resolution === 'custom' || !aspectRatio || aspectRatio === 'custom') {
      appendQuery(query, 'size', payload?.size)
    }
    appendQuery(query, 'resolution', resolution)
    appendQuery(query, 'quality', payloadOptions.quality)
    appendQuery(query, 'aspectRatio', aspectRatio)
    appendPositiveIntegerQuery(query, 'aspectWidth', payloadOptions.aspectWidth)
    appendPositiveIntegerQuery(query, 'aspectHeight', payloadOptions.aspectHeight)
    appendFiniteNumberQuery(query, 'cfg', payloadOptions.cfg)
    appendPositiveIntegerQuery(query, 'steps', payloadOptions.steps)
    if (options.overrideSourceAssetId != null) {
      appendPositiveIntegerQuery(query, 'sourceAssetId', options.overrideSourceAssetId)
    }
    return {
      path: '/generate/image',
      query
    }
  }

  const query: Record<string, string> = {}
  const duration = normalizeText(payload?.duration) || normalizeText(payload?.size)
  appendQuery(query, 'prompt', payload?.prompt ?? source.prompt)
  appendQuery(query, 'model', payload?.modelName ?? source.modelName)
  appendPositiveIntegerQuery(query, 'providerId', payload?.providerId)
  appendQuery(query, 'duration', duration)
  appendQuery(query, 'cameraMotion', payloadOptions.cameraMotion)
  appendFiniteNumberQuery(query, 'motionSpeed', payloadOptions.motionSpeed)
  appendPositiveIntegerQuery(query, 'sourceAssetId', payload?.sourceAssetId)
  appendPositiveIntegerQuery(query, 'endAssetId', payload?.endAssetId)
  return {
    path: '/generate/video',
    query
  }
}
