import { createContext, useContext } from 'react'
import type { IVerifyEmailApi } from '../api/IVerifyEmailApi'

export const VerifyEmailApiContext = createContext<IVerifyEmailApi | null>(null)

/** Port injecté par VerifyEmailApiProvider (ADR 0024, point 7). */
export function useVerifyEmailApi(): IVerifyEmailApi {
  const api = useContext(VerifyEmailApiContext)
  if (api === null) {
    throw new Error('useVerifyEmailApi doit être appelé sous un VerifyEmailApiProvider')
  }
  return api
}
