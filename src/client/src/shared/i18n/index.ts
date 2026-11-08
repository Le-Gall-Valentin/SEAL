// API publique du segment shared/i18n (ADR 0017) : le mécanisme de traduction, sans aucun texte métier ; chaque
// tranche déclare ses textes dans son segment i18n.
export { LanguageProvider, useMessages } from './LanguageProvider'
export { defineMessages, type Language, type MessageParams, type Messages } from './messages'
export { translate } from './translate'
