import { commands } from 'vitest/browser'
import { describeEnvelopeVectors } from './testing/envelopeVectorSuite'
import { messageParties } from './testing/messageParties'
import { describeMessageOpen } from './testing/messageOpenSuite'
import { describeMessageSeal } from './testing/messageSealSuite'
import { record } from './testing/vectorReader'

// QA-02 : envelope.json rejoué dans Chromium (BoringSSL), le moteur du client. L'ouverture vérifie le certificat de
// l'émetteur par l'Autorité épinglée, dans IndexedDB : elle ne se teste que dans le navigateur.
// Chemins relatifs à la racine du client (src/client), comme les résout commands.readFile.
const read = async (file: string) =>
  record(JSON.parse(await commands.readFile(`../protocol/vectors/${file}`)))
const envelope = await read('envelope.json')
const parties = await messageParties(
  envelope,
  await read('vault.json'),
  await read('identity.json'),
)

describeEnvelopeVectors(envelope, 'Chromium')
describeMessageSeal(envelope, parties, 'Chromium')
describeMessageOpen(envelope, parties, 'Chromium')
