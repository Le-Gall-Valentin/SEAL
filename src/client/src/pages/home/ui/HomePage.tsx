import { useMessages } from '@/shared/i18n'
import { homeMessages } from '../i18n/messages'

/** Page provisoire du lot 0 : l'écran de connexion de la maquette la remplace au lot 1. */
export function HomePage() {
  const t = useMessages(homeMessages)
  return (
    <main className="flex min-h-screen items-center justify-center">
      <h1 className="text-[34px] leading-none font-extrabold tracking-tighter">{t('wordmark')}</h1>
    </main>
  )
}
