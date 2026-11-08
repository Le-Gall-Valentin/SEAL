// API publique du segment shared/i18n (ADR 0017) : le mécanisme de traduction, sans aucun texte métier ; chaque
// tranche déclare ses textes dans son segment i18n. translate reste interne (Q4).
export { LanguageProvider, useMessages } from './LanguageProvider'
export { defineMessages, hasMessage, type Language } from './messages'
