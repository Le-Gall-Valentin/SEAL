import type { AvatarTint } from '@/shared/config'

const TINT_CLASS: Record<AvatarTint, string> = {
  ice: 'bg-av-ice',
  moss: 'bg-av-moss',
  birch: 'bg-av-birch',
  heather: 'bg-av-heather',
  clay: 'bg-av-clay',
  lichen: 'bg-av-lichen',
}

// Tailles de la maquette : jeton (24) et option (36) du sélecteur d'amis, profil de la navigation (40), ligne d'ami
// (44), paramètres (56 et 64).
const SIZE_CLASS: Record<AvatarProps['size'], string> = {
  24: 'size-6 text-[10px]',
  36: 'size-9 text-[13px]',
  40: 'size-10 text-[13px]',
  44: 'size-11 text-[16px]',
  56: 'size-14 text-[20px]',
  64: 'size-16 text-[23px]',
}

interface AvatarProps {
  readonly initials: string
  /** Sans teinte : celle du compte de la session dans la maquette (moss). */
  readonly tint?: AvatarTint | undefined
  readonly size: 24 | 36 | 40 | 44 | 56 | 64
}

/** Pastille ronde à initiales, décorative : le nom de la personne est lu à côté. */
export function Avatar({ initials, tint = 'moss', size }: AvatarProps) {
  return (
    <span
      aria-hidden="true"
      className={`grid flex-none place-items-center rounded-full font-semibold text-ink-2 ${TINT_CLASS[tint]} ${SIZE_CLASS[size]}`}
    >
      {initials}
    </span>
  )
}
