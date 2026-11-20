/**
 * Constantes du protocole partagées par plusieurs structures de shared/crypto (PROTOCOL.md, sections 4 à 6). Une valeur
 * fixée par le CDC ou un ADR n'est écrite qu'ici.
 */

/** ADR 0025 (point 7) et ADR 0027 (point 9) : retard d'horloge toléré entre le client et le serveur. */
export const CLOCK_SKEW_MS = 5 * 60_000

/** KEY-21 : une sous-clé vaut 45 jours. */
export const SUBKEY_VALIDITY_MS = 45 * 86_400_000

/** KEY-05 : identifiant d'une sous-clé, 16 octets aléatoires. */
export const EK_ID_BYTES = 16

/** KEY-04, PROTOCOL.md 4.2 : numéro de série d'un certificat, 16 octets. */
export const SERIAL_BYTES = 16

/** CDC 7.3 : CEK AES-256 d'un message. */
export const CEK_BYTES = 32

/** KEY-04, PROTOCOL.md 4.2 : identifiant de la clé de l'Autorité, les 8 premiers octets de son empreinte. */
export const KEY_ID_BYTES = 8

/** RFC 8032 : clé publique Ed25519 encodée (et R d'une signature). */
export const ED25519_PUBLIC_KEY_BYTES = 32

/** RFC 7748 : clé publique X25519, et `enc` de DHKEM(X25519) (RFC 9180). */
export const X25519_PUBLIC_KEY_BYTES = 32

/** NIST SP 800-38D : nonce AES-GCM de 96 bits, pour le coffre comme pour les messages. */
export const AES_GCM_NONCE_BYTES = 12

/** ADR 0027 : clé de coffre (VDK), AES-256. */
export const VDK_BYTES = 32
