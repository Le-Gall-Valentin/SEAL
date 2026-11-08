import { createContext, use, type ReactNode } from 'react'
import type { Language, MessageParams, Messages } from './messages'
import { translate } from './translate'

const LanguageContext = createContext<Language>('fr')

interface LanguageProviderProps {
  readonly language: Language
  readonly children: ReactNode
}

/** Langue courante de l'interface, montée par app ; le français sans fournisseur. */
export function LanguageProvider({ language, children }: LanguageProviderProps) {
  return <LanguageContext value={language}>{children}</LanguageContext>
}

/** Fonction t() typée sur les clés des textes d'une tranche, dans la langue courante (ADR 0017). */
export function useMessages<Key extends string>(
  messages: Messages<Key>,
): (key: Key, params?: MessageParams) => string {
  const language = use(LanguageContext)
  return (key, params) => translate(messages, language, key, params)
}
