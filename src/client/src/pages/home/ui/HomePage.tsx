import { t } from '@/shared/i18n'

/** Page provisoire du lot 0 : l'écran de connexion de la maquette la remplace au lot 1. */
export function HomePage() {
  return (
    <main className="flex min-h-screen items-center justify-center">
      <h1 className="text-[34px] leading-none font-extrabold tracking-tighter">
        {t('home.wordmark')}
      </h1>
    </main>
  )
}
