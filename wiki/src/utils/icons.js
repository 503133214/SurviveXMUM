// 内容图标：数据库里每个篇章/页面的 icon 字段存的是一个 emoji（后端字段、
// 投稿表单都按这个约定），界面上统一换成同一套线性图标（Lucide）显示。
//
// 解析顺序：标题关键词（更贴合具体页面）→ emoji 对照表 → 所在篇章的图标 → 通用文档图标。
// 新内容用了表里没有的 emoji 也不会露出 emoji，而是退回篇章图标。
import {
  Activity, AlarmClock, ArrowLeftRight, Atom, Award, Banknote, BedDouble, Bike, Bookmark,
  BookMarked, BookOpen, BookOpenCheck, BookOpenText, Bot, BriefcaseBusiness, Building,
  Building2, Bus, CalendarDays, Camera, CarTaxiFront, ChartColumn, CircleCheck, CircleHelp,
  CircuitBoard, Clapperboard, ClipboardList, CodeXml, Coffee, Coins, Compass, CookingPot,
  CreditCard, Dumbbell, Earth, Eye, FileText, Fish, Flag, FlaskConical, Gamepad2, Globe,
  GraduationCap, Hammer, Heart, Hotel, Hourglass, House, IdCard, KeyRound, Keyboard, Landmark,
  Languages, Laptop, Leaf, LibraryBig, Lightbulb, Lock, Luggage, Map as MapIcon, MapPin, Medal, Megaphone,
  MessagesSquare, Mic, Microscope, Monitor, Music, Newspaper, NotebookPen, Package, PartyPopper,
  PawPrint, PenLine, Pill, Pin, Plane, PlaneLanding, Plug, Radio, School, ScrollText, SearchCheck,
  ShieldCheck, ShoppingBag, ShoppingCart, Sigma, Smartphone, Soup, Sparkles, Sprout, Star,
  Stethoscope, Target, Telescope, TestTube, TrainFront, TrendingUp, TriangleAlert, Trophy, Tv,
  UsersRound, UtensilsCrossed, Volleyball, WashingMachine, Waves, Wrench, Zap,
} from 'lucide-vue-next'

// 篇章 → 图标（slug 与显示名都认，页面数据里 category 两种写法都有）
const CATEGORY_ICONS = {
  人生篇: Compass,
  入学篇: PlaneLanding,
  生活篇: House,
  学习篇: BookOpen,
  专业篇: LibraryBig,
  升学篇: GraduationCap,
  走进社会篇: BriefcaseBusiness,
  访谈篇: Mic,
  api: CodeXml,
  开发文档: CodeXml,
}

// emoji → 图标。键已去掉变体选择符（U+FE0F），查表前会做同样处理。
const EMOJI_ICONS = {
  // 现有内容里用到的
  '✍': PenLine, '🌏': Earth, '🌍': Earth, '🌎': Earth, '📡': Radio, '🏫': School, '👀': Telescope,
  '✈': Plane, '🖥': Monitor, '🔑': KeyRound, '🛡': ShieldCheck, '💯': Award, '🏠': House,
  '🍜': UtensilsCrossed, '🏛': Landmark, '🏨': Hotel, '🏘': Building2, '💳': CreditCard,
  '🐱': PawPrint, '🎌': Sparkles, '🛒': ShoppingCart, '🏥': Stethoscope, '🗺': MapIcon, '🏃': Activity,
  '🇲🇾': Flag, '🎓': GraduationCap, '⌨': Keyboard, '🌐': Globe, '🛠': Wrench, '📊': ChartColumn,
  '🔄': ArrowLeftRight, '🔬': Microscope, '📚': BookMarked, '👥': UsersRound, '🎯': Target,
  '📖': BookOpenText, '💰': Coins, '📈': TrendingUp, '🔤': Languages, '📢': Megaphone,
  '📰': Newspaper, '🔐': Lock, '📐': Sigma, '⚛': Atom, '💻': Laptop, '🤖': Bot, '🌊': Waves,
  '🐋': Fish, '🌿': Leaf, '⚗': FlaskConical, '📟': CircuitBoard, '🎬': Clapperboard,
  '📝': NotebookPen, '🌱': Sprout, '🏅': Medal, '💼': BriefcaseBusiness, '🎤': MessagesSquare,
  '🏢': Building, '💡': Lightbulb, '🎙': Mic, '🔌': Plug, '🧭': Compass,
  // 编辑器图标选择器里的其余选项，以及常见写法
  '🛏': BedDouble, '🍱': Soup, '☕': Coffee, '🧺': WashingMachine, '🔧': Wrench, '🚌': Bus,
  '🚕': CarTaxiFront, '🚄': TrainFront, '🛂': IdCard, '🪪': IdCard, '🏦': Landmark, '💊': Pill,
  '🩺': Stethoscope, '⚽': Volleyball, '🏀': Volleyball, '🎮': Gamepad2, '🎵': Music,
  '📱': Smartphone, '📋': ClipboardList, '📅': CalendarDays, '⏰': AlarmClock, '⚠': TriangleAlert,
  '❓': CircleHelp, '✅': CircleCheck, '⭐': Star, '🌟': Star, '🎉': PartyPopper, '❤': Heart,
  '📄': FileText, '📃': FileText, '📌': Pin, '📍': MapPin, '🚲': Bike, '📷': Camera, '📦': Package,
  '🧪': TestTube, '💵': Banknote, '🏆': Trophy, '⚡': Zap, '🔨': Hammer, '🍳': CookingPot,
  '🛍': ShoppingBag, '📺': Tv, '🔖': Bookmark, '👁': Eye, '🧳': Luggage, '📗': BookOpenCheck,
  '🔍': SearchCheck, '🏗': Building2, '💬': MessagesSquare, '⏳': Hourglass,
}

// 少数页面按标题关键词取更贴切的图标（多是原本没有 emoji 的页面）
const TITLE_ICONS = [
  [/机场/, PlaneLanding],
  [/宿舍|住宿/, BedDouble],
  [/行前|行李/, Luggage],
  [/latex/i, Sigma],
  [/论文|毕业设计/, ScrollText],
  [/保研|推免/, Award],
  [/招生|报考/, ClipboardList],
  [/英语|雅思|托福|语言要求/, Languages],
  [/自学/, Lightbulb],
]

// 后端徽章按 id 固定图标（BadgeCatalog）
const BADGE_ICONS = {
  'first-contribution': Sprout,
  'ten-contributions': BookOpenCheck,
  'fifty-contributions': LibraryBig,
  'page-creator': PenLine,
  'prolific-creator': Building2,
  proofreader: SearchCheck,
  generalist: Compass,
  discussant: MessagesSquare,
  veteran: Hourglass,
}

// 编辑器里可选的图标（存进数据库的仍是对应 emoji）
export const ICON_CHOICES = [
  '📚', '📖', '📝', '🎓', '🏫', '🧭', '🗺', '🌏',
  '🏠', '🛏', '🍜', '☕', '🛒', '🧺', '🔧', '🏦',
  '🚌', '🚕', '✈', '🚄', '🛂', '💳', '💰', '📈',
  '🏥', '💊', '🏃', '⚽', '🎮', '🎬', '🎵', '📷',
  '📱', '💻', '🌐', '🔌', '📋', '📅', '⏰', '🎯',
  '💡', '⚠', '❓', '✅', '⭐', '🎉', '❤', '💼',
]

// 选择器需要可读名称，读屏和搜索都不依赖 emoji 的系统读法。
const ICON_LABELS = {
  '📚': '书籍', '📖': '阅读', '📝': '笔记', '🎓': '升学', '🏫': '学校', '🧭': '指南', '🗺': '地图', '🌏': '世界',
  '🏠': '生活', '🛏': '住宿', '🍜': '餐饮', '☕': '咖啡', '🛒': '购物', '🧺': '洗衣', '🔧': '维修', '🏦': '银行',
  '🚌': '公交', '🚕': '出租车', '✈': '飞机', '🚄': '火车', '🛂': '证件', '💳': '银行卡', '💰': '费用', '📈': '发展',
  '🏥': '医疗', '💊': '药品', '🏃': '运动', '⚽': '球类', '🎮': '游戏', '🎬': '电影', '🎵': '音乐', '📷': '摄影',
  '📱': '手机', '💻': '电脑', '🌐': '网络', '🔌': '电器', '📋': '清单', '📅': '日历', '⏰': '时间', '🎯': '目标',
  '💡': '提示', '⚠': '注意', '❓': '问答', '✅': '完成', '⭐': '推荐', '🎉': '活动', '❤': '关怀', '💼': '工作',
}

export function iconLabel(icon) {
  const key = normalizeEmoji(icon)
  return key ? ICON_LABELS[key] || '其他图标' : '未选择图标'
}

export function normalizeEmoji(value) {
  // 去掉变体选择符，并只取 ZWJ 组合里的第一个（🏃‍♀ → 🏃）
  return String(value || '').replace(/️/g, '').split('‍')[0].trim()
}

export function emojiIcon(emoji) {
  const key = normalizeEmoji(emoji)
  if (!key) return null
  if (EMOJI_ICONS[key]) return EMOJI_ICONS[key]
  const first = String.fromCodePoint(key.codePointAt(0))
  return EMOJI_ICONS[first] || null
}

export function categoryIcon(category) {
  return CATEGORY_ICONS[(category || '').trim()] || null
}

export function badgeIcon(badge) {
  return (badge && (BADGE_ICONS[badge.id] || emojiIcon(badge.icon))) || Award
}

/**
 * 页面/篇章的显示图标。
 * @param {{ icon?: string, title?: string, category?: string, kind?: 'page'|'category' }} opts
 */
export function resolveIcon({ icon, title, category, kind = 'page' } = {}) {
  if (kind === 'category') {
    return categoryIcon(category) || categoryIcon(title) || emojiIcon(icon) || BookOpen
  }
  const byTitle = TITLE_ICONS.find(([re]) => re.test(title || ''))
  if (byTitle) return byTitle[1]
  return emojiIcon(icon) || categoryIcon(category) || FileText
}
