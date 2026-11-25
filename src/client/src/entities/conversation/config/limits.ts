/** GRP-01, ADR 0040 : nom d'un groupe, en points de code après NFC. */
export const GROUP_NAME_MAX_LENGTH = 64
/** SEC-01 : borne brute d'un nom de groupe reçu, en unités UTF-16 ; le serveur le rend en NFC. */
export const GROUP_NAME_RAW_MAX_LENGTH = 128
/** ADR 0041 (point 9) : borne de saisie, en unités UTF-16 ; un nom valide collé décomposé (NFD) y tient. */
export const GROUP_NAME_INPUT_MAX_LENGTH = 256
/** GRP-01 : 50 membres actifs au plus, créateur compris. */
export const MAX_MEMBERS = 50
