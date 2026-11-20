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
