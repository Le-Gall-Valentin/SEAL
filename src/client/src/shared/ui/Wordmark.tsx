import { useMessages } from '@/shared/i18n'
import { brandMessages } from './brandMessages'
import { Sceau } from './Sceau'

interface WordmarkProps {
  /** « panel » : panneau de Connexion-Ordinateur ; « header » : en-tête du téléphone, agrandi sur tablette. */
  readonly variant: 'panel' | 'header'
}

/** Logo « seal » suivi du sceau vérifié ; un span, pour tenir dans un titre (accueil provisoire). */
export function Wordmark({ variant }: WordmarkProps) {
  const t = useMessages(brandMessages)
  return variant === 'panel' ? (
    <span className="flex items-baseline gap-0.75">
      <span className="text-[34px] leading-none font-extrabold tracking-[-0.05em]">
        {t('wordmark')}
      </span>
      <Sceau variant="verified" size={10} />
    </span>
  ) : (
    <span className="flex items-baseline gap-0.5">
      <span className="text-[26px] leading-none font-extrabold tracking-[-0.05em] sm:text-[28px]">
        {t('wordmark')}
      </span>
      <Sceau variant="verified" size={8} />
    </span>
  )
}
