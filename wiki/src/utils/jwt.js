// JWT 的 payload 段是 base64url（用 - 和 _ 代替 + 和 /，且不带 padding），
// 直接 atob 会在遇到这些字符或非 ASCII 载荷时抛错。统一在这里补齐成标准
// base64 再按 UTF-8 解码；解析失败返回 null，由调用方决定回退行为。
export function parseJwtPayload(token) {
  const part = String(token || '').split('.')[1]
  if (!part) return null
  try {
    const base64 = part
      .replace(/-/g, '+')
      .replace(/_/g, '/')
      .padEnd(part.length + ((4 - (part.length % 4)) % 4), '=')
    const binary = atob(base64)
    const bytes = Uint8Array.from(binary, (ch) => ch.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes))
  } catch {
    return null
  }
}
