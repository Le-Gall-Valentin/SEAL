/**
 * KEY-30 : propose `text` au téléchargement sous `fileName`. Le lien blob est révoqué juste après le clic, au tour
 * suivant de la boucle d'événements, pour que tout navigateur ait lancé le téléchargement : le texte ne reste ni dans le
 * document ni derrière une adresse (ADR 0029, point 7).
 */
export function downloadText(fileName: string, text: string): void {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }))
  try {
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.click()
  } finally {
    setTimeout(() => {
      URL.revokeObjectURL(url)
    }, 0)
  }
}
