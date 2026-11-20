import { createPort } from '@/shared/lib'
import type { IKeysApi } from '../api/IKeysApi'

// ADR 0024, point 7 : port IKeysApi, fourni par la racine de composition ou par une doublure dans les tests.
const port = createPort<IKeysApi>('KeysApiProvider')

export const KeysApiProvider = port.Provider
export const useKeysApi = port.usePort
