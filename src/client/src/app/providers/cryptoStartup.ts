import { checkCryptoSupport, type KeyStore, openKeyStore } from '@/shared/crypto'

interface CryptoStartupDependencies {
  readonly checkSupport?: () => Promise<boolean>
  readonly openStore?: () => Promise<KeyStore>
  /** Appelé seulement pour un navigateur accepté : un navigateur refusé n'envoie aucune requête (ADR 0027, point 8). */
  readonly onSupported: () => void
}

/**
 * KEY-01, KEY-40 : démarrage cryptographique du client. Contrôle d'Ed25519 et de X25519, puis ouverture du trousseau
 * de l'appareil ; null pour un navigateur non pris en charge. Chaque appel recommence : « Réessayer » rouvre ainsi le
 * trousseau après un refus d'IndexedDB (ADR 0027, point 8).
 */
export function createCryptoStartup({
  checkSupport = checkCryptoSupport,
  openStore = openKeyStore,
  onSupported,
}: CryptoStartupDependencies): () => Promise<KeyStore | null> {
  return async () => {
    if (!(await checkSupport())) {
      return null
    }
    onSupported()
    return openStore()
  }
}
