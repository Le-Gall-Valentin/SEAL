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
  addSubkey,
  generateKeyring,
  type Keyring,
  type NewKeyring,
  openVault,
  redateSubkey,
  reopenVault,
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
// sealMessage lève HpkeSealError pour une sous-clé de destinataire inutilisable (RFC 9180, 7.1.4).
export { HpkeSealError } from './hpke'
// sealMessage lève aussi MessageFormatError (brouillon hors du format) et Base64UrlError (numéro de série illisible).
export { Base64UrlError } from './base64url'
export { type EnvelopeWire, MessageFormatError, type WrapWire } from './messageFormat'
export { type MessageDraft, MessageTooLongError, sealMessage } from './sealMessage'
export {
  type IncomingContext,
  type MessageRejection,
  type MessageWarning,
  type OpenedMessage,
  openMessage,
  type ReceivedMessage,
} from './openMessage'
export { compareOwnKeys, type KeysComparison, neverPublished, type ServedKeys } from './ownKeys'
export {
  type ChosenSubkey,
  chooseSubkey,
  type ServedSubkey,
  type ServedUserKeys,
  SubkeyUnavailableError,
  type SubkeyUnavailableReason,
} from './recipientKeys'
