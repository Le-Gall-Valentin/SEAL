package fr.vlegall.seal.keys.infrastructure.crypto;

import fr.vlegall.seal.crypto.Randoms;
import fr.vlegall.seal.keys.domain.port.out.RandomBytes;
import org.springframework.stereotype.Component;

/** Octets tirés par {@code SecureRandom}, dans {@code crypto}, seul accès à la JCA (ADR 0001). */
@Component("keysRandomBytes")
public class RandomBytesAdapter implements RandomBytes {

    @Override
    public byte[] next(int count) {
        return Randoms.bytes(count);
    }
}
