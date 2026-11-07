package fr.vlegall.archfixture.crypto;

import org.springframework.util.StringUtils;

/** Interdit : un package crypto qui dépend d'autre chose que du JDK. */
public class CryptoUsingSpring {
    boolean present(String value) {
        return StringUtils.hasText(value);
    }
}
