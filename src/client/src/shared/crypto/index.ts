// API publique du segment shared/crypto (ADR 0012, point 8) : des structures typées seulement, comme
// encodeSignedPayload, jamais une primitive générique ni un sign(bytes). L'encodeur canonique et la vérification
// Ed25519 restent internes au segment, leurs tests compris.
export { checkCryptoSupport } from './support'
