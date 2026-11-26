import { createPort } from '@/shared/lib'
import type { IRealtimeApi } from '../api/IRealtimeApi'

// ADR 0024, point 7 : port IRealtimeApi, fourni par la racine de composition ou par une doublure.
const port = createPort<IRealtimeApi>('RealtimeApiProvider')

export const RealtimeApiProvider = port.Provider
export const useRealtimeApi = port.usePort
