import { createContext, useContext } from 'react'
import type { IPasswordResetApi } from '../api/IPasswordResetApi'

export const PasswordResetApiContext = createContext<IPasswordResetApi | null>(null)

/** Port injecté par PasswordResetApiProvider (ADR 0024, point 7). */
export function usePasswordResetApi(): IPasswordResetApi {
  const api = useContext(PasswordResetApiContext)
  if (api === null) {
    throw new Error('usePasswordResetApi doit être appelé sous un PasswordResetApiProvider')
  }
  return api
}
