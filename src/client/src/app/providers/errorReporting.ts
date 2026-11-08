import type { RootOptions } from 'react-dom/client'

const ignore = () => undefined

/**
 * SEC-21 (ADR 0012, audit, Q2) : en production, React n'écrit dans la console ni une erreur de rendu ni sa pile,
 * qu'elle soit captée par une frontière ou non ; le développement garde ses traces.
 */
export function rootErrorOptions(production: boolean): RootOptions {
  return production
    ? { onCaughtError: ignore, onUncaughtError: ignore, onRecoverableError: ignore }
    : {}
}

/** SEC-21 : de même pour React Router, qui sinon écrit l'erreur d'une route dans la console. */
export function routerErrorHandler(production: boolean): (() => undefined) | undefined {
  return production ? ignore : undefined
}
