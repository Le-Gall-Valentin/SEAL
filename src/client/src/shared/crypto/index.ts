// API publique du segment shared/crypto (ADR 0012, point 8) : des structures typées seulement, comme
// encodeSignedPayload, jamais une primitive générique ni un sign(bytes). L'encodeur canonique et la vérification
// Ed25519 restent internes au segment, leurs tests compris.
export { checkCryptoSupport } from './support'
export {
  generateRecoveryKey,
  parseRecoveryKey,
  pickRecoveryGroups,
  type RecoveryKey,
  RecoveryKeyFormatError,
} from './recovery'
export {
  generateKeyring,
  type Keyring,
  type NewKeyring,
  openVault,
  type SubkeyInfo,
} from './keyring'
export {
  type VaultPayload,
  VaultOpenError,
  type VaultRejection,
  vaultFromWire,
  vaultToWire,
  type VaultWire,
} from './vault'
export {
  bindSubkey,
  type IdentityProof,
  proveIdentity,
  type SubkeyBindingWire,
} from './publication'
export { type KeyStore, openKeyStore, type StoredCertificate } from './keystore'
export { type AuthorityCheck, checkAuthority, type ServedAuthority } from './authority'
export {
  CertificateRejectedError,
  type CertificateRejection,
  type ExpectedOwner,
  type ServedCertificate,
  storeOwnCertificate,
  verifyOwnCertificate,
} from './certificate'
