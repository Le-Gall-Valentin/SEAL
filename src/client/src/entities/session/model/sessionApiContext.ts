import { createContext, useContext } from 'react'
import type { ISessionApi } from '../api/ISessionApi'

export const SessionApiContext = createContext<ISessionApi | null>(null)

/** Port injecté par SessionApiProvider (ADR 0024, point 7). */
export function useSessionApi(): ISessionApi {
  const api = useContext(SessionApiContext)
  if (api === null) {
    throw new Error('useSessionApi doit être appelé sous un SessionApiProvider')
  }
  return api
}
