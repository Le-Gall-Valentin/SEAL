// API publique du segment shared/crypto (ADR 0012, point 8) : des structures typées seulement, comme
// encodeSignedPayload, jamais une primitive générique ni un sign(bytes). L'encodeur canonique et la vérification
// Ed25519 restent internes au segment, leurs tests compris.
export { checkCryptoSupport } from './support'
export {
  parseRecoveryKey,
  pickRecoveryGroups,
  type RecoveryKey,
  RecoveryKeyFormatError,
} from './recovery'
export { generateKeyring, type Keyring, type NewKeyring, openVault, type Subkey } from './keyring'
export { type VaultPayload, VaultOpenError } from './vault'
export { type KeyStore, openKeyStore, type StoredCertificate } from './keystore'
export { type AuthorityCheck, checkAuthority, type ServedAuthority } from './authority'
