/**
 * Tête de la mascotte (pose « tete » du composant Phoque de la maquette), logo de la navigation principale. Dessin
 * repris tel quel : ses couleurs ne suivent pas le thème. Décorative : le lien qui la porte se nomme « SEAL ».
 */
export function PhoqueHead({ size }: { readonly size: 32 | 34 }) {
  return (
    <svg
      viewBox="0 0 32 32"
      aria-hidden="true"
      className={`block flex-none ${size === 32 ? 'size-8' : 'size-8.5'}`}
    >
      <circle cx="16" cy="16.5" r="13.2" fill="#FBFCF7" stroke="#1B1F14" strokeWidth="2.2" />
      <ellipse cx="11.2" cy="15" rx="2.1" ry="2.5" fill="#1B1F14" />
      <ellipse cx="20.8" cy="15" rx="2.1" ry="2.5" fill="#1B1F14" />
      <path
        d="M14 19H18C18.8 19 19.2 20 18.6 20.5L16.7 22.1C16.3 22.4 15.7 22.4 15.3 22.1L13.4 20.5C12.8 20 13.2 19 14 19Z"
        fill="#1B1F14"
      />
    </svg>
  )
}
