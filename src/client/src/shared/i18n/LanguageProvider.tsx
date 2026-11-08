import { createContext, use, useCallback, type ReactNode } from 'react'
import type { Language, Messages, ParamsOf, Texts } from './messages'
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

/**
 * Fonction t() typée sur les clés et les paramètres des textes d'une tranche, dans la langue courante (ADR 0017). Sa
 * référence ne change qu'avec la langue ou les textes : elle peut figurer dans les dépendances d'un effet.
 */
export function useMessages<Fr extends Texts>(messages: Messages<Fr>) {
  const language = use(LanguageContext)
  return useCallback(
    <Key extends Extract<keyof Fr, string>>(key: Key, ...params: ParamsOf<Fr[Key]>): string =>
      translate(messages, language, key, params[0]),
    [messages, language],
  )
}
