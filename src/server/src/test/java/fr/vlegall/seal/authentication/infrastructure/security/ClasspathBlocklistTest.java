package fr.vlegall.seal.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClasspathBlocklistTest {

    /** Empreinte publiée dans PROTOCOL.md, section 3.1. */
    private static final String PUBLISHED_SHA256 = "0e44bfef3adb46364f09225c8e8bc2299169b1df1f020c49538a6aeafe12006d";

    private static byte[] resource() throws IOException {
        try (InputStream in = ClasspathBlocklist.class.getResourceAsStream("/password-blocklist.txt")) {
            assertThat(in).as("liste embarquée comme ressource Maven").isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    @DisplayName("AUTH-11 : la liste embarquée est celle de PROTOCOL.md, à l'octet près")
    void auth11EmbeddedListMatchesProtocol() throws IOException, NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(resource());

        assertThat(HexFormat.of().formatHex(digest)).isEqualTo(PUBLISHED_SHA256);
    }

    @Test
    @DisplayName("AUTH-11 : la licence MIT accompagne la liste dans les ressources du serveur")
    void auth11LicenceIsEmbeddedWithTheList() throws IOException {
        try (InputStream in = ClasspathBlocklist.class.getResourceAsStream("/password-blocklist.LICENSE")) {
            assertThat(in).as("licence embarquée comme ressource Maven").isNotNull();
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .startsWith("MIT License")
                    .contains("Daniel Miessler");
        }
    }

    @Test
    @DisplayName("AUTH-11 : 10 000 entrées uniques, toutes de 15 caractères ou plus")
    void auth11ListHasTenThousandLongEntries() throws IOException {
        List<String> lines =
                new String(resource(), StandardCharsets.UTF_8).lines().toList();

        assertThat(lines).hasSize(10_000);
        assertThat(new HashSet<>(lines)).hasSize(10_000);
        assertThat(lines).allMatch(line -> line.codePointCount(0, line.length()) >= 15);
    }

    @Test
    @DisplayName("AUTH-11 : la liste reconnaît ses entrées sous leur forme repliée, et rien d'autre")
    void auth11BlocklistRecognisesFoldedEntries() {
        ClasspathBlocklist blocklist = new ClasspathBlocklist();

        assertThat(blocklist.contains("mailcreated5240")).isTrue();
        assertThat(blocklist.contains("123456789987654321")).isTrue();
        assertThat(blocklist.contains("Mailcreated5240"))
                .as("l'appelant replie la saisie")
                .isFalse();
        assertThat(blocklist.contains("le phoque nage dans le port")).isFalse();
    }
}
