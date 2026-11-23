// API publique de la tranche keyring (ADR 0012, ADR 0029) : trousseau de l'appareil et API des clés du compte.
// completeKeys, keyStatus et saveVaultOnce restent internes : ils exigent le verrou des clés, que seuls useKeyStatus,
// setupKeys, restoreKeys et resetIdentity savent prendre ou tenir (ADR 0029, point 3).
export type {
  CertificateView,
  IdentityProofBody,
  IdentityResetBody,
  IKeysApi,
  KemName,
  OwnKeysView,
  SubkeyBody,
  SubkeyView,
  VaultView,
} from './api/IKeysApi'
export { keysApi } from './api/keysApi'
export { ForeignIdentityError, type KeyOwner, StaleVaultError } from './model/completeKeys'
export { KEYS_LOCK, type KeyStatus, type KeyStatusState, useKeyStatus } from './model/keyStatus'
export { KeysApiProvider, useKeysApi } from './model/keysApiPort'
export { KeyStoreProvider, useKeyStore } from './model/keyStorePort'
export { InvalidVaultError, restoreKeys, WrongRecoveryKeyError } from './model/restoreKeys'
export { prepareReset, resetIdentity } from './model/resetIdentity'
export { setupKeys } from './model/setupKeys'
