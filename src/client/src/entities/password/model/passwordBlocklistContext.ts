import { createContext, useContext } from 'react'
import type { IPasswordBlocklist } from '../api/passwordBlocklist'

export const PasswordBlocklistContext = createContext<IPasswordBlocklist | null>(null)

/** Port injecté par PasswordBlocklistProvider (ADR 0024, point 7). */
export function usePasswordBlocklistApi(): IPasswordBlocklist {
  const api = useContext(PasswordBlocklistContext)
  if (api === null) {
    throw new Error('usePasswordBlocklistApi doit être appelé sous un PasswordBlocklistProvider')
  }
  return api
}
