import { createContext, useContext } from 'react'
import type { ILogoutApi } from '../api/ILogoutApi'

export const LogoutApiContext = createContext<ILogoutApi | null>(null)

/** Port injecté par LogoutApiProvider (ADR 0024, point 7). */
export function useLogoutApi(): ILogoutApi {
  const api = useContext(LogoutApiContext)
  if (api === null) {
    throw new Error('useLogoutApi doit être appelé sous un LogoutApiProvider')
  }
  return api
}
