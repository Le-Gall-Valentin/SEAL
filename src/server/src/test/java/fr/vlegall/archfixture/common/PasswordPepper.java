package fr.vlegall.archfixture.common;

/** Témoin du poivre des mots de passe, rangé dans common comme celui de SEAL. */
public class PasswordPepper {
    public byte[] value() {
        return new byte[32];
    }
}
