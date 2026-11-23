import { createPort } from '@/shared/lib'
import type { IPasswordChangeApi } from '../api/IPasswordChangeApi'

// ADR 0024, point 7 : port IPasswordChangeApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IPasswordChangeApi>('PasswordChangeApiProvider')

export const PasswordChangeApiProvider = port.Provider
export const usePasswordChangeApi = port.usePort
