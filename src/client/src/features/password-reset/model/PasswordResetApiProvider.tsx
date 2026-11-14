import type { ReactNode } from 'react'
import type { IPasswordResetApi } from '../api/IPasswordResetApi'
import { PasswordResetApiContext } from './passwordResetApiContext'

interface PasswordResetApiProviderProps {
  readonly api: IPasswordResetApi
  readonly children: ReactNode
}

/** Injecte l'implémentation du port IPasswordResetApi, choisie par la racine de composition. */
export function PasswordResetApiProvider({ api, children }: PasswordResetApiProviderProps) {
  return <PasswordResetApiContext value={api}>{children}</PasswordResetApiContext>
}
