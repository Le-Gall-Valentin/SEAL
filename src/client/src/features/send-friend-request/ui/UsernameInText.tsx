interface UsernameInTextProps {
  /** Phrase déjà traduite, qui contient `username`. */
  readonly text: string
  readonly username: string
}

/** L'identifiant d'une phrase en police mono, comme dans la maquette (Amis-Ordinateur, Amis-Telephone). */
export function UsernameInText({ text, username }: UsernameInTextProps) {
  const at = text.indexOf(username)
  if (at < 0) {
    return text
  }
  return (
    <>
      {text.slice(0, at)}
      <span className="font-mono text-[12.5px] text-ink">{username}</span>
      {text.slice(at + username.length)}
    </>
  )
}
