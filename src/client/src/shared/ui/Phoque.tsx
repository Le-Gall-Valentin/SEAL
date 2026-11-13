import { useMessages } from '@/shared/i18n'
import { brandMessages } from './brandMessages'
import { SEAL_PATH } from './sealPath'

/** Poses de la mascotte utilisées par les écrans (composant Phoque de la maquette). */
export type PhoqueVariant = 'waving' | 'neutral' | 'searching'

const SIZES = { 64: 'size-16', 88: 'size-22', 200: 'size-50' } as const

interface PhoqueProps {
  readonly variant: PhoqueVariant
  readonly size: keyof typeof SIZES
}

const LABELS = {
  waving: 'phoqueWaving',
  neutral: 'phoqueNeutral',
  searching: 'phoqueSearching',
} as const

/** La mascotte, dessin repris tel quel de la maquette : ses couleurs ne suivent pas le thème. */
export function Phoque({ variant, size }: PhoqueProps) {
  const t = useMessages(brandMessages)
  return (
    <svg
      viewBox="0 0 128 128"
      role="img"
      aria-label={t(LABELS[variant])}
      className={`block flex-none ${SIZES[size]}`}
    >
      {variant === 'waving' && <Waving />}
      {variant === 'neutral' && <Neutral />}
      {variant === 'searching' && <Searching />}
    </svg>
  )
}

function Waving() {
  return (
    <>
      <ellipse cx="64" cy="116" rx="34" ry="4.5" fill="#1B1F14" opacity="0.08" />
      <path
        d="M31 53C23 47 14 37 12.5 28C11.5 21.5 17 18.5 21.5 22C26.5 26 30.5 34 34.5 42"
        fill="#EDF0E4"
        stroke="#1B1F14"
        strokeWidth="3.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M64 20C91 20 109 41 109 67C109 93 91 112 64 112C37 112 19 93 19 67C19 41 37 20 64 20Z"
        fill="#FBFCF7"
      />
      <path
        d="M22 84C30 101 46 110 64 110C82 110 98 101 106 84C96 95 81 101 64 101C47 101 32 95 22 84Z"
        fill="#E3E8D5"
      />
      <path
        d="M64 20C91 20 109 41 109 67C109 93 91 112 64 112C37 112 19 93 19 67C19 41 37 20 64 20Z"
        fill="none"
        stroke="#1B1F14"
        strokeWidth="3.5"
      />
      <path
        d="M100 87C108 92 112 100 108 105C104 110 95 105 90 96"
        fill="#EDF0E4"
        stroke="#1B1F14"
        strokeWidth="3.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M5.5 36C3.5 31 3.5 25.5 6 21M9.5 13.5C13 10.5 17 9.5 21 10"
        fill="none"
        stroke="#66741C"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <ellipse cx="36.5" cy="75" rx="6.5" ry="3.6" fill="#EBCFC2" opacity="0.75" />
      <ellipse cx="91.5" cy="75" rx="6.5" ry="3.6" fill="#EBCFC2" opacity="0.75" />
      <path
        d="M39.5 64C41.5 57.5 50.5 57.5 52.5 64M75.5 64C77.5 57.5 86.5 57.5 88.5 64"
        fill="none"
        stroke="#1B1F14"
        strokeWidth="3.6"
        strokeLinecap="round"
      />
      <path
        d="M84 76L96 73M84 80.5L96 81.5M44 76L36 74.5M44 80.5L36 81"
        stroke="#1B1F14"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
      <path d="M59.5 83.5C61 89.5 67 89.5 68.5 83.5Z" fill="#1B1F14" />
      <ellipse cx="57" cy="77.5" rx="8" ry="6" fill="#FFFFFF" stroke="#1B1F14" strokeWidth="2.4" />
      <ellipse cx="71" cy="77.5" rx="8" ry="6" fill="#FFFFFF" stroke="#1B1F14" strokeWidth="2.4" />
      <circle cx="53.5" cy="77" r="1.1" fill="#1B1F14" />
      <circle cx="56.5" cy="80" r="1.1" fill="#1B1F14" />
      <circle cx="74.5" cy="77" r="1.1" fill="#1B1F14" />
      <circle cx="71.5" cy="80" r="1.1" fill="#1B1F14" />
      <path
        d="M59.6 69.6H68.4C70.2 69.6 71 71.7 69.7 72.9L65.6 76.4C64.7 77.2 63.3 77.2 62.4 76.4L58.3 72.9C57 71.7 57.8 69.6 59.6 69.6Z"
        fill="#1B1F14"
      />
      <path
        d="M55.5 89.5L64 95.5L72.5 89.5"
        fill="none"
        stroke="#4F5B14"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        transform="translate(54.4 87.4) scale(1.2)"
        d={SEAL_PATH}
        fill="#66741C"
        stroke="#1B1F14"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path
        d="M60.4 97.3L62.9 99.7L67.7 94.8"
        fill="none"
        stroke="#EEF1E2"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </>
  )
}

function Neutral() {
  return (
    <>
      <ellipse cx="64" cy="116" rx="34" ry="4.5" fill="#1B1F14" opacity="0.08" />
      <path
        d="M64 20C91 20 109 41 109 67C109 93 91 112 64 112C37 112 19 93 19 67C19 41 37 20 64 20Z"
        fill="#FBFCF7"
      />
      <path
        d="M22 84C30 101 46 110 64 110C82 110 98 101 106 84C96 95 81 101 64 101C47 101 32 95 22 84Z"
        fill="#E3E8D5"
      />
      <path
        d="M64 20C91 20 109 41 109 67C109 93 91 112 64 112C37 112 19 93 19 67C19 41 37 20 64 20Z"
        fill="none"
        stroke="#1B1F14"
        strokeWidth="3.5"
      />
      <path
        d="M28 87C20 92 16 100 20 105C24 110 33 105 38 96"
        fill="#EDF0E4"
        stroke="#1B1F14"
        strokeWidth="3.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M100 87C108 92 112 100 108 105C104 110 95 105 90 96"
        fill="#EDF0E4"
        stroke="#1B1F14"
        strokeWidth="3.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <ellipse cx="36.5" cy="75" rx="6.5" ry="3.6" fill="#EBCFC2" opacity="0.75" />
      <ellipse cx="91.5" cy="75" rx="6.5" ry="3.6" fill="#EBCFC2" opacity="0.75" />
      <ellipse cx="46" cy="62" rx="6.2" ry="7.4" fill="#1B1F14" />
      <ellipse cx="82" cy="62" rx="6.2" ry="7.4" fill="#1B1F14" />
      <circle cx="48.3" cy="59" r="2.3" fill="#FFFFFF" />
      <circle cx="84.3" cy="59" r="2.3" fill="#FFFFFF" />
      <path
        d="M44 76L32 73M44 80.5L32 81.5M84 76L96 73M84 80.5L96 81.5"
        stroke="#1B1F14"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
      <ellipse cx="57" cy="77.5" rx="8" ry="6" fill="#FFFFFF" stroke="#1B1F14" strokeWidth="2.4" />
      <ellipse cx="71" cy="77.5" rx="8" ry="6" fill="#FFFFFF" stroke="#1B1F14" strokeWidth="2.4" />
      <circle cx="53.5" cy="77" r="1.1" fill="#1B1F14" />
      <circle cx="56.5" cy="80" r="1.1" fill="#1B1F14" />
      <circle cx="74.5" cy="77" r="1.1" fill="#1B1F14" />
      <circle cx="71.5" cy="80" r="1.1" fill="#1B1F14" />
      <path
        d="M59.6 69.6H68.4C70.2 69.6 71 71.7 69.7 72.9L65.6 76.4C64.7 77.2 63.3 77.2 62.4 76.4L58.3 72.9C57 71.7 57.8 69.6 59.6 69.6Z"
        fill="#1B1F14"
      />
      <path
        d="M55.5 89.5L64 95.5L72.5 89.5"
        fill="none"
        stroke="#4F5B14"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        transform="translate(54.4 87.4) scale(1.2)"
        d={SEAL_PATH}
        fill="#66741C"
        stroke="#1B1F14"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path
        d="M60.4 97.3L62.9 99.7L67.7 94.8"
        fill="none"
        stroke="#EEF1E2"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </>
  )
}

function Searching() {
  return (
    <>
      <ellipse cx="64" cy="116" rx="34" ry="4.5" fill="#1B1F14" opacity="0.08" />
      <path
        d="M64 20C91 20 109 41 109 67C109 93 91 112 64 112C37 112 19 93 19 67C19 41 37 20 64 20Z"
        fill="#FBFCF7"
      />
      <path
        d="M22 84C30 101 46 110 64 110C82 110 98 101 106 84C96 95 81 101 64 101C47 101 32 95 22 84Z"
        fill="#E3E8D5"
      />
      <path
        d="M64 20C91 20 109 41 109 67C109 93 91 112 64 112C37 112 19 93 19 67C19 41 37 20 64 20Z"
        fill="none"
        stroke="#1B1F14"
        strokeWidth="3.5"
      />
      <path
        d="M28 87C20 92 16 100 20 105C24 110 33 105 38 96"
        fill="#EDF0E4"
        stroke="#1B1F14"
        strokeWidth="3.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <ellipse cx="36.5" cy="75" rx="6.5" ry="3.6" fill="#EBCFC2" opacity="0.75" />
      <ellipse cx="91.5" cy="75" rx="6.5" ry="3.6" fill="#EBCFC2" opacity="0.75" />
      <ellipse cx="44" cy="62" rx="6.2" ry="7.4" fill="#1B1F14" />
      <circle cx="46.8" cy="59.5" r="2.3" fill="#FFFFFF" />
      <path
        d="M44 76L32 73M44 80.5L32 81.5"
        stroke="#1B1F14"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
      <ellipse cx="57" cy="77.5" rx="8" ry="6" fill="#FFFFFF" stroke="#1B1F14" strokeWidth="2.4" />
      <ellipse cx="71" cy="77.5" rx="8" ry="6" fill="#FFFFFF" stroke="#1B1F14" strokeWidth="2.4" />
      <circle cx="53.5" cy="77" r="1.1" fill="#1B1F14" />
      <circle cx="56.5" cy="80" r="1.1" fill="#1B1F14" />
      <circle cx="74.5" cy="77" r="1.1" fill="#1B1F14" />
      <circle cx="71.5" cy="80" r="1.1" fill="#1B1F14" />
      <path
        d="M59.6 69.6H68.4C70.2 69.6 71 71.7 69.7 72.9L65.6 76.4C64.7 77.2 63.3 77.2 62.4 76.4L58.3 72.9C57 71.7 57.8 69.6 59.6 69.6Z"
        fill="#1B1F14"
      />
      <circle cx="64" cy="88" r="2.6" fill="#1B1F14" />
      <circle cx="84" cy="59" r="16" fill="#EEF1E2" opacity="0.9" />
      <ellipse cx="85" cy="60" rx="8.6" ry="10" fill="#1B1F14" />
      <circle cx="88.5" cy="56" r="3.2" fill="#FFFFFF" />
      <circle cx="84" cy="59" r="16" fill="none" stroke="#4F5B14" strokeWidth="4.2" />
      <path d="M96 71L107 83" stroke="#4F5B14" strokeWidth="6.5" strokeLinecap="round" />
      <path
        d="M98 97C108 96 114 88 111 82C108 76 99 79 95 87"
        fill="#EDF0E4"
        stroke="#1B1F14"
        strokeWidth="3.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </>
  )
}
