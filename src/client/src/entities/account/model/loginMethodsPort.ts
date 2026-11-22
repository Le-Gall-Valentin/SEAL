import { createPort } from '@/shared/lib'
import type { ILoginMethodsApi } from '../api/ILoginMethodsApi'

// ADR 0024, point 7 : port ILoginMethodsApi, fourni par la racine de composition ou par une doublure.
const port = createPort<ILoginMethodsApi>('LoginMethodsApiProvider')

export const LoginMethodsApiProvider = port.Provider
export const useLoginMethodsApi = port.usePort
