export interface IpGeoInfo {
  ip: string
  source: string
  locationSummary: string
  continent: string
  country: string
  region: string
  city: string
  postalCode: string
  timezoneId: string
  timezoneUtc: string
  timezoneAbbr: string
  latitude: number | null
  longitude: number | null
  isp: string
  organization: string
  asn: number | null
  privateNetwork: boolean | null
  proxy: boolean | null
  vpn: boolean | null
  tor: boolean | null
  hosting: boolean | null
  detailMessage: string
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function normalizeOptionalText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeOptionalNumber(value: unknown): number | null {
  if (value == null || value === '') {
    return null
  }
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized : null
}

function normalizeOptionalBoolean(value: unknown): boolean | null {
  if (value === true || value === 'true' || value === 1 || value === '1') return true
  if (value === false || value === 'false' || value === 0 || value === '0') return false
  return null
}

export function normalizeIpGeoInfo(value: unknown): IpGeoInfo | null {
  if (!isPlainObject(value)) {
    return null
  }
  return {
    ip: normalizeOptionalText(value.ip),
    source: normalizeOptionalText(value.source),
    locationSummary: normalizeOptionalText(value.locationSummary),
    continent: normalizeOptionalText(value.continent),
    country: normalizeOptionalText(value.country),
    region: normalizeOptionalText(value.region),
    city: normalizeOptionalText(value.city),
    postalCode: normalizeOptionalText(value.postalCode),
    timezoneId: normalizeOptionalText(value.timezoneId),
    timezoneUtc: normalizeOptionalText(value.timezoneUtc),
    timezoneAbbr: normalizeOptionalText(value.timezoneAbbr),
    latitude: normalizeOptionalNumber(value.latitude),
    longitude: normalizeOptionalNumber(value.longitude),
    isp: normalizeOptionalText(value.isp),
    organization: normalizeOptionalText(value.organization),
    asn: normalizeOptionalNumber(value.asn),
    privateNetwork: normalizeOptionalBoolean(value.privateNetwork),
    proxy: normalizeOptionalBoolean(value.proxy),
    vpn: normalizeOptionalBoolean(value.vpn),
    tor: normalizeOptionalBoolean(value.tor),
    hosting: normalizeOptionalBoolean(value.hosting),
    detailMessage: normalizeOptionalText(value.detailMessage)
  }
}

export function formatIpGeoSummary(value: IpGeoInfo | null | undefined) {
  return value?.locationSummary?.trim() || '-'
}
