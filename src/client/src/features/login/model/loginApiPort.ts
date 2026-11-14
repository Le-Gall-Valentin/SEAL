import { createPort } from '@/shared/lib'
import type { ILoginApi } from '../api/ILoginApi'

// ADR 0024, point 7 : port ILoginApi, fourni par la racine de composition ou par une doublure dans les tests.
const port = createPort<ILoginApi>('LoginApiProvider')

export const LoginApiProvider = port.Provider
export const useLoginApi = port.usePort
