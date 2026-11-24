import type { ReactNode } from 'react'
import type { AvatarTint } from '@/shared/config'
import { Avatar } from '@/shared/ui'

interface RequestRowProps {
  readonly initials: string
  readonly tint: AvatarTint
  /** Première ligne : le nom affiché d'une invitation reçue, l'identifiant d'une envoyée (API-01). */
  readonly title: ReactNode
  readonly details: ReactNode
  readonly actions: ReactNode
}

/** Ligne d'invitation d'Amis-Tablette : avatar, texte, actions, qui passent à la ligne sur un écran étroit. */
export function RequestRow({ initials, tint, title, details, actions }: RequestRowProps) {
  return (
    <li className="flex flex-wrap items-center gap-3 border-b border-line py-3 pr-3 pl-4 last:border-b-0">
      <Avatar initials={initials} tint={tint} size={44} />
      <div className="flex min-w-0 flex-1 flex-col">
        {title}
        {details}
      </div>
      <div className="flex flex-wrap items-center gap-2">{actions}</div>
    </li>
  )
}
