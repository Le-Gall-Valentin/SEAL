import type { ReactNode } from 'react'
import type { ISessionApi } from '../api/ISessionApi'
import { SessionApiContext } from './sessionApiContext'

interface SessionApiProviderProps {
  readonly api: ISessionApi
  readonly children: ReactNode
}

/** Injecte l'implémentation du port ISessionApi, choisie par la racine de composition. */
export function SessionApiProvider({ api, children }: SessionApiProviderProps) {
  return <SessionApiContext value={api}>{children}</SessionApiContext>
}
