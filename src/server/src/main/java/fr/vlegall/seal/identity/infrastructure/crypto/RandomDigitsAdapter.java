package fr.vlegall.seal.identity.infrastructure.crypto;

import fr.vlegall.seal.crypto.Randoms;
import fr.vlegall.seal.identity.domain.port.out.RandomDigits;
import org.springframework.stereotype.Component;

/** Chiffres tirés par {@code SecureRandom}, dans {@code crypto}, seul accès à la JCA (ADR 0001). */
@Component
public class RandomDigitsAdapter implements RandomDigits {

    @Override
    public String next(int length) {
        return Randoms.digits(length);
    }
}
