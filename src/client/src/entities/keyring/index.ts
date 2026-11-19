// API publique de la tranche keyring (ADR 0012, ADR 0029) : trousseau de l'appareil et API des clés du compte.
export type {
  CertificateView,
  IdentityProofBody,
  IKeysApi,
  KemName,
  OwnKeysView,
  SubkeyBody,
  SubkeyView,
  VaultView,
} from './api/IKeysApi'
export { keysApi } from './api/keysApi'
export {
  completeKeys,
  ForeignIdentityError,
  type KeyOwner,
  type KeysContext,
  StaleVaultError,
} from './model/completeKeys'
export { KEYS_LOCK, type KeyStatus, keyStatus, useKeyStatus } from './model/keyStatus'
export { KeysApiProvider, useKeysApi } from './model/keysApiPort'
export { KeyStoreProvider, useKeyStore } from './model/keyStorePort'
export { InvalidVaultError, restoreKeys, WrongRecoveryKeyError } from './model/restoreKeys'
export { saveVaultOnce, VaultTakenError } from './model/saveVault'
export { setupKeys } from './model/setupKeys'
