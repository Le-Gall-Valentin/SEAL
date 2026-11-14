import type { ReactNode } from 'react'
import type { ILogoutApi } from '../api/ILogoutApi'
import { LogoutApiContext } from './logoutApiContext'

interface LogoutApiProviderProps {
  readonly api: ILogoutApi
  readonly children: ReactNode
}

/** Injecte l'implémentation du port ILogoutApi, choisie par la racine de composition. */
export function LogoutApiProvider({ api, children }: LogoutApiProviderProps) {
  return <LogoutApiContext value={api}>{children}</LogoutApiContext>
}
