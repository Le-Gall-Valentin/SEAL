import { createPort } from '@/shared/lib'
import type { KeyStore } from '@/shared/crypto'

// KEY-40, ADR 0012 : le trousseau de l'appareil, ouvert une fois par la racine de composition et injecté.
const port = createPort<KeyStore>('KeyStoreProvider')

export const KeyStoreProvider = port.Provider
export const useKeyStore = port.usePort
