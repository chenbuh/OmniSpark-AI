export const MIN_PASSWORD_LENGTH = 8

const COMMON_WEAK_PASSWORDS = new Set([
  '123456',
  '12345678',
  '123456789',
  '1234567890',
  '111111',
  '11111111',
  '000000',
  '00000000',
  'password',
  'password123',
  'qwerty',
  'qwerty123',
  'abc123',
  'iloveyou'
])

function normalize(value?: string | null) {
  return (value || '').trim().toLowerCase()
}

export function validatePasswordStrength(password: string, username?: string) {
  if (!password) {
    return '请输入密码'
  }
  if (password.length < MIN_PASSWORD_LENGTH) {
    return `密码长度不能少于 ${MIN_PASSWORD_LENGTH} 位`
  }
  if (COMMON_WEAK_PASSWORDS.has(normalize(password))) {
    return '密码过于简单，请更换为更安全的密码'
  }
  if (username && normalize(password) === normalize(username)) {
    return '密码不能与账号相同'
  }
  return null
}

export const PASSWORD_REQUIREMENT_TEXT = `至少 ${MIN_PASSWORD_LENGTH} 位，避免使用常见弱密码或与账号相同`
