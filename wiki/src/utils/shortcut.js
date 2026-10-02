// 搜索快捷键的提示文案：苹果设备（Mac / iPhone / iPad）显示 ⌘K，其余显示 Ctrl K。
// 顶栏搜索入口和首页问答区共用这一处判断，免得各写一份正则、两处显示不一致。
//
// navigator 作为参数传入：单测里可以直接构造，node 20 / 预渲染等没有 navigator
// 的环境也只会退回 Ctrl K，不会抛错。
// iPadOS 13 起 platform 报 MacIntel，同样落在 Mac 分支，正好是想要的结果。
const APPLE_PLATFORM = /Mac|iPhone|iPad/i

export function shortcutLabel(nav = globalThis.navigator) {
  if (!nav) return 'Ctrl K'
  // navigator.platform 已被标为废弃，Chromium 优先用 userAgentData；都拿不到时再看 UA
  const platform = nav.userAgentData?.platform || nav.platform || nav.userAgent || ''
  return APPLE_PLATFORM.test(String(platform)) ? '⌘K' : 'Ctrl K'
}
