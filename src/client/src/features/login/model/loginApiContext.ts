import { createContext, useContext } from 'react'
import type { ILoginApi } from '../api/ILoginApi'

export const LoginApiContext = createContext<ILoginApi | null>(null)

/** Port injecté par LoginApiProvider (ADR 0024, point 7). */
export function useLoginApi(): ILoginApi {
  const api = useContext(LoginApiContext)
  if (api === null) {
    throw new Error('useLoginApi doit être appelé sous un LoginApiProvider')
  }
  return api
}
