import { describeHpke } from './testing/hpkeSuite'

// KEY-22 : HPKE dans Chromium (BoringSSL), le moteur du client.
describeHpke('Chromium')
