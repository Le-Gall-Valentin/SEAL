import type { Language, MessageParams, Messages } from './messages'

const PLACEHOLDER = /\{(\w+)\}/g

/** Texte d'une clé dans une langue, le français à défaut, paramètres interpolés ; un paramètre absent reste visible. */
export function translate<Key extends string>(
  messages: Messages<Key>,
  language: Language,
  key: Key,
  params?: MessageParams,
): string {
  const text = (language === 'en' ? messages.en?.[key] : undefined) ?? messages.fr[key]
  if (params === undefined) {
    return text
  }
  return text.replace(PLACEHOLDER, (placeholder, name: string) => {
    const value = params[name]
    return value === undefined ? placeholder : String(value)
  })
}
