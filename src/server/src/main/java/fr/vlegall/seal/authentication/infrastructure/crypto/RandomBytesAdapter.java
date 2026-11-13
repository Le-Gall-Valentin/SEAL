package fr.vlegall.seal.authentication.infrastructure.crypto;

import fr.vlegall.seal.authentication.domain.port.out.RandomBytes;
import fr.vlegall.seal.crypto.Randoms;
import org.springframework.stereotype.Component;

/** Octets tirés par {@code SecureRandom}, dans {@code crypto}, seul accès à la JCA (ADR 0001). */
@Component
public class RandomBytesAdapter implements RandomBytes {

    @Override
    public byte[] next(int count) {
        return Randoms.bytes(count);
    }
}
