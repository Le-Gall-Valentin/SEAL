package fr.vlegall.archfixture.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Permis : du Java pur dans crypto. */
public class PureCrypto {
    byte[] digest(byte[] input) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }
}
