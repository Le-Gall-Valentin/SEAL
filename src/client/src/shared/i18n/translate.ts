import type { Language, MessageParams, Messages, Texts } from './messages'

const PLACEHOLDER = /\{(\w+)\}/g

type LanguageTexts = Readonly<Partial<Record<string, string>>>

/** Propriété propre d'un objet, jamais une propriété héritée d'Object.prototype (constructor, __proto__…). */
function own(texts: LanguageTexts | undefined, key: string): string | undefined {
  return texts !== undefined && Object.hasOwn(texts, key) ? texts[key] : undefined
}

/**
 * Texte d'une clé dans une langue, le français à défaut (texte absent ou vide), paramètres interpolés ; un paramètre
 * absent reste visible. Une clé inconnue lève une erreur, prise par la frontière d'erreur (ADR 0017, Q5).
 */
export function translate<Fr extends Texts>(
  messages: Messages<Fr>,
  language: Language,
  key: string,
  params?: MessageParams,
): string {
  const french = own(messages.fr, key)
  if (french === undefined) {
    throw new Error(`Texte inconnu : ${key}`)
  }
  const localized = own(Object.hasOwn(messages, language) ? messages[language] : undefined, key)
  const text = localized === undefined || localized === '' ? french : localized
  if (params === undefined) {
    return text
  }
  return text.replace(PLACEHOLDER, (placeholder, name: string) =>
    Object.hasOwn(params, name) ? String(params[name]) : placeholder,
  )
}
