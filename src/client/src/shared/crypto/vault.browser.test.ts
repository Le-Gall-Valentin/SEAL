import { commands } from 'vitest/browser'
import { describeVaultVectors } from './testing/vaultVectorSuite'
import { record } from './testing/vectorReader'

// QA-02 : vault.json rejoué dans Chromium (BoringSSL), et non seulement sous Node (OpenSSL, comme le générateur).
// Chemin relatif à la racine du client (src/client), comme le résout commands.readFile.
describeVaultVectors(
  record(JSON.parse(await commands.readFile('../protocol/vectors/vault.json'))),
  'Chromium',
)
