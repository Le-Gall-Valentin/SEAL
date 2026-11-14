import { createPort } from '@/shared/lib'
import type { IVerifyEmailApi } from '../api/IVerifyEmailApi'

// ADR 0024, point 7 : port IVerifyEmailApi, fourni par la racine de composition ou par une doublure dans les tests.
const port = createPort<IVerifyEmailApi>('VerifyEmailApiProvider')

export const VerifyEmailApiProvider = port.Provider
export const useVerifyEmailApi = port.usePort
