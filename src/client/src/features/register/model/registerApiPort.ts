import { createPort } from '@/shared/lib'
import type { IRegisterApi } from '../api/IRegisterApi'

// ADR 0024, point 7 : port IRegisterApi, fourni par la racine de composition ou par une doublure dans les tests.
const port = createPort<IRegisterApi>('RegisterApiProvider')

export const RegisterApiProvider = port.Provider
export const useRegisterApi = port.usePort
