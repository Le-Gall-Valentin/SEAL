import { createPort } from '@/shared/lib'
import type { IPasswordResetApi } from '../api/IPasswordResetApi'

// ADR 0024, point 7 : port IPasswordResetApi, fourni par la racine de composition ou par une doublure dans les tests.
const port = createPort<IPasswordResetApi>('PasswordResetApiProvider')

export const PasswordResetApiProvider = port.Provider
export const usePasswordResetApi = port.usePort
