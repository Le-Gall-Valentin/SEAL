import { commands } from 'vitest/browser'
import { describeEnvelopeVectors } from './testing/envelopeVectorSuite'
import { describeMessageSeal } from './testing/messageSealSuite'
import { record } from './testing/vectorReader'

// QA-02 : envelope.json rejoué dans Chromium (BoringSSL), le moteur du client.
// Chemins relatifs à la racine du client (src/client), comme les résout commands.readFile.
const envelope = record(JSON.parse(await commands.readFile('../protocol/vectors/envelope.json')))
const vault = record(JSON.parse(await commands.readFile('../protocol/vectors/vault.json')))

describeEnvelopeVectors(envelope, 'Chromium')
describeMessageSeal(envelope, vault, 'Chromium')
