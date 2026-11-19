import { useMessages } from '@/shared/i18n'
import { StateCard, StatePage } from '@/shared/ui'
import { unsupportedBrowserPageMessages } from '../i18n'

/** KEY-01 : page pleine, sans bouton : recharger n'y changerait rien (ADR 0027, point 1). */
export function UnsupportedBrowserPage() {
  const t = useMessages(unsupportedBrowserPageMessages)
  return (
    <StatePage>
      <StateCard heading="h1" title={t('title')} text={t('text')} />
    </StatePage>
  )
}
