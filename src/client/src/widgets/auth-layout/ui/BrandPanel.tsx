import type { ReactNode } from 'react'
import { useMessages } from '@/shared/i18n'
import { Empreinte } from '@/entities/key'
import { Phoque, Sceau, Wordmark } from '@/shared/ui'
import { authLayoutMessages } from '../i18n'

// Empreinte d'exemple du panneau, celle de la maquette.
const PANEL_FINGERPRINT = '29E9C1694564624662CCEA21BAE28389594171A501E7BE963EE4D56DFE10C210'

function Bullet({ icon, children }: { readonly icon: ReactNode; readonly children: ReactNode }) {
  return (
    <li className="flex items-center gap-3.5 text-ink-2">
      <span className="grid size-9 flex-none place-items-center rounded-[10px] bg-surface text-accent-text inset-ring inset-ring-line">
        {icon}
      </span>
      {children}
    </li>
  )
}

/** Panneau de marque de Connexion-Ordinateur, à gauche du formulaire sur ordinateur. */
export function BrandPanel() {
  const t = useMessages(authLayoutMessages)
  return (
    <aside className="hidden min-h-screen flex-[1_1_440px] flex-col justify-between gap-8 border-r border-line bg-accent-soft px-14 py-12 lg:flex">
      <Wordmark variant="panel" />
      <div className="flex max-w-[448px] flex-col gap-7">
        <Phoque variant="waving" size={200} />
        <p className="m-0 text-[34px] leading-[1.12] font-bold tracking-[-0.03em] text-balance">
          {t('tagline')}
        </p>
        <ul className="m-0 flex list-none flex-col gap-4 p-0">
          <Bullet
            icon={
              <svg
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <rect x="5" y="10.5" width="14" height="10" rx="2.5" />
                <path d="M8 10.5v-3a4 4 0 0 1 8 0v3" />
              </svg>
            }
          >
            {t('encrypted')}
          </Bullet>
          <Bullet icon={<Sceau variant="verified" size={18} />}>{t('sealed')}</Bullet>
          <Bullet icon={<Empreinte fingerprint={PANEL_FINGERPRINT} size={20} />}>
            {t('compared')}
          </Bullet>
        </ul>
      </div>
      <p className="m-0 font-mono text-[12px] tracking-[0.08em] text-ink-3 uppercase">
        {t('acronym')}
      </p>
    </aside>
  )
}
