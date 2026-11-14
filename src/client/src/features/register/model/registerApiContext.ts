import { createContext, useContext } from 'react'
import type { IRegisterApi } from '../api/IRegisterApi'

export const RegisterApiContext = createContext<IRegisterApi | null>(null)

/** Port injecté par RegisterApiProvider (ADR 0024, point 7). */
export function useRegisterApi(): IRegisterApi {
  const api = useContext(RegisterApiContext)
  if (api === null) {
    throw new Error('useRegisterApi doit être appelé sous un RegisterApiProvider')
  }
  return api
}
