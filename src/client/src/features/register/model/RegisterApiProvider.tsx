import type { ReactNode } from 'react'
import type { IRegisterApi } from '../api/IRegisterApi'
import { RegisterApiContext } from './registerApiContext'

interface RegisterApiProviderProps {
  readonly api: IRegisterApi
  readonly children: ReactNode
}

/** Injecte l'implémentation du port IRegisterApi, choisie par la racine de composition. */
export function RegisterApiProvider({ api, children }: RegisterApiProviderProps) {
  return <RegisterApiContext value={api}>{children}</RegisterApiContext>
}
