import { t } from '@/shared/i18n'
import styles from './HomePage.module.css'

/** Page provisoire du lot 0 : l'écran de connexion de la maquette la remplace au lot 1. */
export function HomePage() {
  return (
    <main className={styles['page']}>
      <h1 className={styles['wordmark']}>{t('home.wordmark')}</h1>
    </main>
  )
}
