/** Port de la liste de blocage d'AUTH-11 (PROTOCOL.md, section 3.1). */
export interface IPasswordBlocklist {
  /** Entrées brutes, une par ligne ; le modèle les replie comme le serveur. */
  load(): Promise<readonly string[]>
}

/** ADR 0007, point 4 : même fichier que le serveur, en morceau séparé, chargé seulement par les écrans qui l'affichent. */
export const passwordBlocklist: IPasswordBlocklist = {
  async load() {
    const { default: text } = await import('../../../../../protocol/password-blocklist.txt?raw')
    return text.split('\n').filter((line) => line !== '')
  },
}
