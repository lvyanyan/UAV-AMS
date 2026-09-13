/**
 * vue-i18n 实例：locale 持久化在 localStorage('uav-locale')，缺省 zh-CN。
 * setLocale 同步更新 i18n / <html lang> / document.title。
 */
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import en from './en'

export type Locale = 'zh-CN' | 'en'
export const LOCALE_KEY = 'uav-locale'

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: (localStorage.getItem(LOCALE_KEY) as Locale) || 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, en },
  // 标牌/状态等动态拼接的 key 缺失时不刷 dev 告警（渲染处已用 te() 兜底）
  missingWarn: false,
  fallbackWarn: false,
})

/** 当前 locale（响应式，模板/计算属性里读它可随切换刷新） */
export const locale = i18n.global.locale as unknown as { value: Locale }

/** 切换语言：写 localStorage + i18n + <html lang> + 标题 */
export function setLocale(loc: Locale) {
  locale.value = loc
  localStorage.setItem(LOCALE_KEY, loc)
  applyDocumentMeta(loc)
}

function applyDocumentMeta(loc: Locale) {
  document.documentElement.lang = loc
  document.title = i18n.global.t('app.title')
}

// 模块加载即生效（index.html 的 lang/title 由这里动态接管）
applyDocumentMeta(locale.value)

export default i18n
