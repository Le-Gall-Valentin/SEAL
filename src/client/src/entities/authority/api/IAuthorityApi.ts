/** Réponse de GET /api/v1/pki/ca (PROTOCOL.md 4.1) : octets en base64url, empreinte en hexadécimal majuscule. */
export interface AuthorityView {
  readonly publicKey: string
  readonly keyId: string
  readonly fingerprint: string
}

/** Port de la tranche authority (ADR 0024, point 7) : les hooks ne connaissent que lui. */
export interface IAuthorityApi {
  /** KEY-11 : clé de l'Autorité servie, que le client compare à celle qu'il a épinglée. */
  fetchAuthority(signal?: AbortSignal): Promise<AuthorityView>
}
