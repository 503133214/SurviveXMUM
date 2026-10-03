// 通知、公告、致谢墙里的链接由后台录入，打开或渲染成 href 之前统一在这里分类。
//
// 规则跟后端 LinkUtil 一致：只认「/ 开头的站内路径」和「带主机名的 http(s) 网址」，
// 其余（javascript:、data:、mailto:、//host、带反斜杠或控制字符的）一律当作没有链接。
// 两类必须分开打开：http(s) 交给 router.push 会被当成相对路径拼到当前路由后面，
// 跳到一个不存在的站内页面；站内路径交给 window.open 又会多开一个标签页。

// 控制字符和反斜杠：浏览器解析 URL 时会悄悄去掉或改写它们，可能把看起来无害的链接变成别的地址
const UNSAFE_CHARS = /[\u0000-\u001f\u007f\\]/

/**
 * @param {unknown} link 后端给的 link 字段
 * @param {string} [origin] 本站 origin（location.origin）；同源的完整网址按站内路径处理
 * @returns {{ kind: 'internal', to: string } | { kind: 'external', href: string } | null}
 */
export function resolveLink(link, origin) {
  if (typeof link !== 'string') return null
  const value = link.trim()
  if (!value || UNSAFE_CHARS.test(value)) return null

  if (value.startsWith('/')) {
    // 「//host」是协议相对地址，会离开本站。
    // 站内路径里的空格放行：后端加 LinkUtil 之前存下的通知，路径里可能带标题原文的空格，router 能处理
    return value.startsWith('//') ? null : { kind: 'internal', to: value }
  }

  if (!/^https?:\/\//i.test(value) || /\s/.test(value)) return null
  let url
  try {
    url = new URL(value)
  } catch {
    return null
  }
  if (!url.hostname) return null
  if (origin && url.origin === origin) {
    return { kind: 'internal', to: `${url.pathname}${url.search}${url.hash}` }
  }
  return { kind: 'external', href: url.href }
}
