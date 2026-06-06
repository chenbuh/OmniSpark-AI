function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData<T>(response: unknown, errorMessage: string): T {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data as T
}

function requirePagePayload<T>(value: unknown, errorMessage: string) {
  if (!isPlainObject(value)) {
    throw new Error(errorMessage)
  }
  const total = Number(value.total)
  const pages = Number(value.pages)
  const records = value.records
  if (!Number.isFinite(total) || total < 0 || !Number.isFinite(pages) || pages < 0 || !Array.isArray(records)) {
    throw new Error(errorMessage)
  }
  return {
    total,
    pages,
    records: records as T[]
  }
}

export async function collectAllPageRecords<T>(options: {
  loadPage: (page: number, pageSize: number) => Promise<unknown>
  errorMessage: string
  pageSize?: number
  maxPages?: number
}) {
  const pageSize = options.pageSize ?? 100
  const maxPages = options.maxPages ?? 1000
  const allRecords: T[] = []
  let expectedTotal: number | null = null
  let page = 1

  while (page <= maxPages) {
    const response = await options.loadPage(page, pageSize)
    const pageData = requirePagePayload<T>(
      getResponseData(response, options.errorMessage),
      options.errorMessage
    )
    if (expectedTotal === null) {
      expectedTotal = pageData.total
    } else if (pageData.total !== expectedTotal) {
      throw new Error(options.errorMessage)
    }
    allRecords.push(...pageData.records)
    if (allRecords.length > pageData.total) {
      throw new Error(options.errorMessage)
    }
    if (pageData.records.length === 0 || allRecords.length >= pageData.total || page >= pageData.pages) {
      break
    }
    page += 1
  }

  if (page > maxPages) {
    throw new Error(options.errorMessage)
  }
  return allRecords
}
