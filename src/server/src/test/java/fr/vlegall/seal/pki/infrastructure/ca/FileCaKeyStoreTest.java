package fr.vlegall.seal.pki.infrastructure.ca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import fr.vlegall.seal.crypto.Ed25519;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** KEY-10, ADR 0025 (point 12) : la clé de l'Autorité, générée une fois, relue ensuite, jamais remplacée. */
@DisplayName("KEY-10 : clé de l'Autorité SEAL")
class FileCaKeyStoreTest {

    @TempDir
    Path data;

    private Path ca() {
        return data.resolve("ca");
    }

    private static boolean posix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    @Test
    @DisplayName("KEY-10 : au premier démarrage, la paire est générée et signe ce que sa clé publique vérifie")
    void key10FirstStartGeneratesAWorkingPair() {
        FileCaKeyStore store = new FileCaKeyStore(ca());
        byte[] message = new byte[] {1, 2, 3};

        assertThat(ca().resolve(FileCaKeyStore.PRIVATE_KEY_FILE)).isRegularFile();
        assertThat(ca().resolve(FileCaKeyStore.PUBLIC_KEY_FILE)).isRegularFile();
        assertThat(Ed25519.verify(store.key().publicKey(), message, store.sign(message)))
                .isTrue();
    }

    @Test
    @DisplayName("KEY-10 : au démarrage suivant, la même Autorité est relue, sans rien réécrire")
    void key10NextStartReadsTheSameAuthority() throws IOException {
        FileCaKeyStore first = new FileCaKeyStore(ca());
        byte[] privateKey = Files.readAllBytes(ca().resolve(FileCaKeyStore.PRIVATE_KEY_FILE));

        FileCaKeyStore second = new FileCaKeyStore(ca());

        assertThat(second.key()).isEqualTo(first.key());
        assertThat(second.key().fingerprint()).isEqualTo(first.key().fingerprint());
        assertThat(Files.readAllBytes(ca().resolve(FileCaKeyStore.PRIVATE_KEY_FILE)))
                .isEqualTo(privateKey);
    }

    @Test
    @DisplayName("KEY-10 : dossier en 700 et fichiers en 600, sans fichier temporaire laissé derrière")
    void key10FilesAreReadableByTheOwnerOnly() throws IOException {
        assumeThat(posix()).isTrue();

        new FileCaKeyStore(ca());

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(ca())))
                .isEqualTo("rwx------");
        assertThat(PosixFilePermissions.toString(
                        Files.getPosixFilePermissions(ca().resolve(FileCaKeyStore.PRIVATE_KEY_FILE))))
                .isEqualTo("rw-------");
        assertThat(PosixFilePermissions.toString(
                        Files.getPosixFilePermissions(ca().resolve(FileCaKeyStore.PUBLIC_KEY_FILE))))
                .isEqualTo("rw-------");
        try (var entries = Files.list(data)) {
            assertThat(entries).containsExactly(ca());
        }
    }

    @Test
    @DisplayName("ADR 0025, point 12 : un fichier seul arrête le démarrage, sans régénérer l'Autorité")
    void adr0025LoneFileStopsTheStart() throws IOException {
        new FileCaKeyStore(ca());
        Path publicKey = ca().resolve(FileCaKeyStore.PUBLIC_KEY_FILE);
        Files.delete(publicKey);

        assertThatThrownBy(() -> new FileCaKeyStore(ca()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FileCaKeyStore.PUBLIC_KEY_FILE);
        assertThat(publicKey).doesNotExist();
    }

    @Test
    @DisplayName("ADR 0025, point 12 : une clé publique qui n'est pas celle de la clé privée arrête le démarrage")
    void adr0025MismatchedPairStopsTheStart() throws IOException {
        new FileCaKeyStore(ca());
        Files.write(
                ca().resolve(FileCaKeyStore.PUBLIC_KEY_FILE),
                Ed25519.toSubjectPublicKeyInfo(Ed25519.generate().publicKey()));

        assertThatThrownBy(() -> new FileCaKeyStore(ca()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ne vont pas ensemble");
    }

    @Test
    @DisplayName("ADR 0025, point 12 : une clé privée illisible arrête le démarrage")
    void adr0025UnreadablePrivateKeyStopsTheStart() throws IOException {
        new FileCaKeyStore(ca());
        Files.write(ca().resolve(FileCaKeyStore.PRIVATE_KEY_FILE), new byte[] {1, 2, 3});

        assertThatThrownBy(() -> new FileCaKeyStore(ca())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("KEY-10 : une clé privée lisible par d'autres arrête le démarrage")
    void key10WiderPermissionsStopTheStart() throws IOException {
        assumeThat(posix()).isTrue();
        new FileCaKeyStore(ca());
        Files.setPosixFilePermissions(
                ca().resolve(FileCaKeyStore.PRIVATE_KEY_FILE), PosixFilePermissions.fromString("rw-r--r--"));

        assertThatThrownBy(() -> new FileCaKeyStore(ca()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("600");
    }

    @Test
    @DisplayName("KEY-10 : un dossier de données non inscriptible arrête le démarrage")
    void key10ReadOnlyDataDirectoryStopsTheStart() throws IOException {
        assumeThat(posix()).isTrue();
        Files.setPosixFilePermissions(data, PosixFilePermissions.fromString("r-x------"));
        try {
            assertThatThrownBy(() -> new FileCaKeyStore(ca())).isInstanceOf(IllegalStateException.class);
        } finally {
            Files.setPosixFilePermissions(data, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    @DisplayName("KEY-10 : un reste de génération interrompue n'empêche pas une nouvelle génération")
    void key10LeftoverOfAnInterruptedGenerationIsIgnored() throws IOException {
        Files.createDirectory(data.resolve("ca.interrompu.tmp"));

        FileCaKeyStore store = new FileCaKeyStore(ca());

        assertThat(store.key().publicKey()).hasSize(Ed25519.PUBLIC_KEY_BYTES);
    }

    @Test
    @DisplayName(
            "KEY-10, ADR 0025 (point 12) : un dossier ca vide, préparé par l'exploitant, reçoit une nouvelle paire")
    void adr0025EmptyCaDirectoryReceivesAPair() throws IOException {
        Files.createDirectory(ca());

        FileCaKeyStore store = new FileCaKeyStore(ca());

        assertThat(ca().resolve(FileCaKeyStore.PRIVATE_KEY_FILE)).isRegularFile();
        assertThat(store.key().publicKey()).hasSize(Ed25519.PUBLIC_KEY_BYTES);
    }

    @Test
    @DisplayName("KEY-10 : un dossier ca ouvert à d'autres que son propriétaire arrête le démarrage")
    void key10WiderDirectoryPermissionsStopTheStart() throws IOException {
        assumeThat(posix()).isTrue();
        new FileCaKeyStore(ca());
        Files.setPosixFilePermissions(ca(), PosixFilePermissions.fromString("rwxrwxrwx"));

        assertThatThrownBy(() -> new FileCaKeyStore(ca()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("700");
    }

    @Test
    @DisplayName(
            "KEY-10, ADR 0025 (point 12) : des liens symboliques vers une paire rangée ailleurs arrêtent le démarrage")
    void adr0025SymbolicLinksStopTheStart() throws IOException {
        assumeThat(posix()).isTrue();
        new FileCaKeyStore(data.resolve("ailleurs"));
        Path linked = Files.createDirectory(
                ca(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        for (String name : new String[] {FileCaKeyStore.PRIVATE_KEY_FILE, FileCaKeyStore.PUBLIC_KEY_FILE}) {
            Files.createSymbolicLink(
                    linked.resolve(name), data.resolve("ailleurs").resolve(name));
        }

        assertThatThrownBy(() -> new FileCaKeyStore(ca()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lien symbolique");
        Path linkedDirectory = data.resolve("ca-lien");
        Files.createSymbolicLink(linkedDirectory, data.resolve("ailleurs"));
        assertThatThrownBy(() -> new FileCaKeyStore(linkedDirectory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lien symbolique");
    }
}
