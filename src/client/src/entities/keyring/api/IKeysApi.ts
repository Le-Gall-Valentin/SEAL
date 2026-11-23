/**
 * Types « fil » de l'API des clés (ADR 0029) : octets en base64url, dates en millisecondes Unix. L'adaptateur convertit
 * l'ISO 8601 du serveur ; shared/crypto reçoit et rend ces mêmes formes, qu'il décode strictement.
 */

/** Seul mécanisme d'encapsulation accepté avant X-Wing (KEY-05, ADR 0025, point 7). */
export type KemName = 'DHKEM-X25519-HKDF-SHA256'

/** Corps de PUT /api/v1/keys/identity (KEY-03). */
export interface IdentityProofBody {
  readonly ikPublic: string
  readonly challenge: string
  readonly signature: string
}

/** Certificat servi par PUT /keys/identity et GET /users/{id}/keys (KEY-04, KEY-33). */
export interface CertificateView {
  readonly serial: string
  readonly issuerKeyId: string
  readonly subjectId: string
  readonly username: string
  readonly ikPublic: string
  readonly notBefore: number
  readonly notAfter: number
  readonly usage: number
  readonly signature: string
}

/** Sous-clé servie par GET /users/{id}/keys : son certificat porte le numéro de série. */
export interface SubkeyView {
  readonly ekId: string
  readonly kem: KemName
  readonly ekPublic: string
  readonly createdAt: number
  readonly expiresAt: number
  readonly signature: string
}

/** Corps de POST /api/v1/keys/subkeys (KEY-05). */
export interface SubkeyBody extends SubkeyView {
  readonly certSerial: string
}

/** KEY-33 : certificat courant et sous-clés valides de l'appelant (ADR 0029, point 4). */
export interface OwnKeysView {
  readonly certificate: CertificateView
  readonly subkeys: readonly SubkeyView[]
}

/** Coffre de GET et PUT /api/v1/keys/vault (KEY-32, KEY-33). */
export interface VaultView {
  readonly vaultVersion: number
  readonly salt: string
  readonly nonceK: string
  readonly wrappedVdk: string
  readonly nonceB: string
  readonly blob: string
}

/** Corps de POST /api/v1/keys/identity/reset (KEY-34, ADR 0036) : preuve de la nouvelle IK et nouveau coffre. */
export interface IdentityResetBody extends IdentityProofBody {
  readonly vault: VaultView
}

/** Port de la tranche keyring (ADR 0024, point 7) : le modèle ne connaît que lui. */
export interface IKeysApi {
  /** KEY-03 : défi de preuve de possession, 32 octets en base64url. */
  createChallenge(): Promise<string>
  /** KEY-03, KEY-04 : publie IK et rend le certificat émis, à vérifier avant tout usage (KEY-06). */
  publishIdentity(proof: IdentityProofBody): Promise<CertificateView>
  /**
   * KEY-34 : réinitialise l'identité, en mode sudo, et rend le nouveau certificat ; « conflict » si le coffre ne suit
   * plus la version enregistrée (409). 403 REAUTH_REQUIRED remonte tel quel.
   */
  resetIdentity(body: IdentityResetBody): Promise<CertificateView | 'conflict'>
  /** KEY-05 : publie une sous-clé liée au certificat courant. */
  publishSubkey(binding: SubkeyBody): Promise<void>
  /** KEY-33 : le coffre du compte, ou rien s'il n'en a pas. */
  readVault(): Promise<VaultView | undefined>
  /** KEY-32 : « conflict » si la version ne suit pas l'enregistrée (409). */
  saveVault(vault: VaultView): Promise<'saved' | 'conflict'>
  /** KEY-33 : certificat courant et sous-clés valides, ou rien sans certificat courant. */
  readOwnKeys(userId: string): Promise<OwnKeysView | undefined>
}
