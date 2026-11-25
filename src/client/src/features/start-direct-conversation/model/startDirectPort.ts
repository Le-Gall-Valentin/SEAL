import { createPort } from '@/shared/lib'
import type { IStartDirectApi } from '../api/IStartDirectApi'

// ADR 0024, point 7 : port IStartDirectApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IStartDirectApi>('StartDirectApiProvider')

export const StartDirectApiProvider = port.Provider
export const useStartDirectApi = port.usePort
