package fr.vlegall.seal.security.ratelimit;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.StringJoiner;

/**
 * Clé de la limitation de débit par IP (SEC-10, ADR 0021 point 6). Une adresse IPv4, ou IPv4 mappée en IPv6, reste
 * entière ; une adresse IPv6 est agrégée à son préfixe /64, qu'un hôte reçoit d'ordinaire entier : sans cela, la limite
 * se contournerait en changeant d'adresse dans le même /64.
 */
public final class ClientAddress {

    /** Clé partagée par toute adresse illisible : elles s'épuisent ensemble (échec fermé). */
    static final String UNREADABLE = "illisible";

    private static final int PREFIX_GROUPS = 4;

    private ClientAddress() {}

    /** Analyse l'adresse comme un littéral, sans jamais la résoudre par DNS ; l'éventuelle zone ({@code %eth0}) est ignorée. */
    public static String key(String remoteAddr) {
        int zone = remoteAddr.indexOf('%');
        String literal = zone >= 0 ? remoteAddr.substring(0, zone) : remoteAddr;
        InetAddress address;
        try {
            address = InetAddress.ofLiteral(literal);
        } catch (IllegalArgumentException e) {
            return UNREADABLE;
        }
        if (address instanceof Inet4Address) {
            return address.getHostAddress();
        }
        byte[] bytes = address.getAddress();
        StringJoiner prefix = new StringJoiner(":", "", "::/64");
        for (int group = 0; group < PREFIX_GROUPS; group++) {
            prefix.add(Integer.toHexString(((bytes[2 * group] & 0xff) << 8) | (bytes[2 * group + 1] & 0xff)));
        }
        return prefix.toString();
    }
}
