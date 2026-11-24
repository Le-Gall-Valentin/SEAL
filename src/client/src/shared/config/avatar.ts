/**
 * ADR 0038 (point 7) : les six teintes d'avatar de la maquette (Amis-Ordinateur), dans l'ordre où le calcul de la
 * teinte les choisit. Ici plutôt que dans shared/ui : une règle métier les lit sans dépendre du composant.
 */
export const AVATAR_TINTS = ['ice', 'moss', 'birch', 'heather', 'clay', 'lichen'] as const

export type AvatarTint = (typeof AVATAR_TINTS)[number]
