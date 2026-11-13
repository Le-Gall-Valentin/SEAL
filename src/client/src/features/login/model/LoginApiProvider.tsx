import type { ReactNode } from 'react'
import type { ILoginApi } from '../api/ILoginApi'
import { LoginApiContext } from './loginApiContext'

interface LoginApiProviderProps {
  readonly api: ILoginApi
  readonly children: ReactNode
}

/** Injecte l'implémentation du port ILoginApi, choisie par la racine de composition. */
export function LoginApiProvider({ api, children }: LoginApiProviderProps) {
  return <LoginApiContext value={api}>{children}</LoginApiContext>
}
