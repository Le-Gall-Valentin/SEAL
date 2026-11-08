// API publique du segment shared/crypto (ADR 0012, point 8) : des structures typées seulement, comme
// encodeSignedPayload, jamais une primitive générique ni un sign(bytes). Elles arriveront avec le noyau
// cryptographique ; d'ici là, l'encodeur canonique reste interne au segment, ses tests compris.
export {}
