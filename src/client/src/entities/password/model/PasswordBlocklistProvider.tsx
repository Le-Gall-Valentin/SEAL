import type { ReactNode } from 'react'
import type { IPasswordBlocklist } from '../api/passwordBlocklist'
import { PasswordBlocklistContext } from './passwordBlocklistContext'

interface PasswordBlocklistProviderProps {
  readonly api: IPasswordBlocklist
  readonly children: ReactNode
}

/** Injecte la source de la liste de blocage, choisie par la racine de composition. */
export function PasswordBlocklistProvider({ api, children }: PasswordBlocklistProviderProps) {
  return <PasswordBlocklistContext value={api}>{children}</PasswordBlocklistContext>
}
