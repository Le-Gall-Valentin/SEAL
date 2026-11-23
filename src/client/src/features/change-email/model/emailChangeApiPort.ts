import { createPort } from '@/shared/lib'
import type { IEmailChangeApi } from '../api/IEmailChangeApi'

// ADR 0024, point 7 : port IEmailChangeApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IEmailChangeApi>('EmailChangeApiProvider')

export const EmailChangeApiProvider = port.Provider
export const useEmailChangeApi = port.usePort
