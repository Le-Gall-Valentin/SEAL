/** Textes du fichier, traduits par l'appelant. */
export interface RecoveryKeyFileTexts {
  readonly title: string
  readonly account: string
  readonly warning: string
}

/**
 * KEY-30, ADR 0029 (point 7) : le `.txt` de la clé de récupération. Titre, compte, les 8 groupes sur deux lignes de 4,
 * puis l'avertissement du dialogue ; nommé d'après l'identifiant, pour qu'un utilisateur de plusieurs comptes les
 * distingue.
 */
export function recoveryKeyFile(
  username: string,
  groups: readonly string[],
  texts: RecoveryKeyFileTexts,
): { readonly name: string; readonly text: string } {
  return {
    name: `seal-cle-de-recuperation-${username}.txt`,
    text: [
      texts.title,
      texts.account,
      '',
      groups.slice(0, 4).join(' '),
      groups.slice(4).join(' '),
      '',
      texts.warning,
      '',
    ].join('\n'),
  }
}
