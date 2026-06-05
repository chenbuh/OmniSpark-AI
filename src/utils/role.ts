export function formatUserRole(role?: string | null): string {
  const normalizedRole = typeof role === 'string' ? role.trim() : ''

  if (!normalizedRole) return '未设置'
  if (normalizedRole === 'admin') return '超级管理员'
  if (normalizedRole === 'user') return '普通用户'

  return normalizedRole
}
