import type { ReactNode } from 'react'
import type { IVerifyEmailApi } from '../api/IVerifyEmailApi'
import { VerifyEmailApiContext } from './verifyEmailApiContext'

interface VerifyEmailApiProviderProps {
  readonly api: IVerifyEmailApi
  readonly children: ReactNode
}

/** Injecte l'implémentation du port IVerifyEmailApi, choisie par la racine de composition. */
export function VerifyEmailApiProvider({ api, children }: VerifyEmailApiProviderProps) {
  return <VerifyEmailApiContext value={api}>{children}</VerifyEmailApiContext>
}
