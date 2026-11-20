import { commands } from 'vitest/browser'
import { describeEnvelopeVectors } from './testing/envelopeVectorSuite'
import { record } from './testing/vectorReader'

// QA-02 : envelope.json rejoué dans Chromium (BoringSSL), le moteur du client.
// Chemin relatif à la racine du client (src/client), comme le résout commands.readFile.
describeEnvelopeVectors(
  record(JSON.parse(await commands.readFile('../protocol/vectors/envelope.json'))),
  'Chromium',
)
