package fr.vlegall.seal.pki.infrastructure.ca;

import fr.vlegall.seal.common.SealProperties;
import fr.vlegall.seal.crypto.Canonical;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.pki.domain.model.AuthorityKey;
import fr.vlegall.seal.pki.domain.port.out.AuthoritySigner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Clé de l'Autorité SEAL (KEY-10, ADR 0025 point 12) : {@code seal-ca.p8} (PKCS#8) et {@code seal-ca.pub}
 * (SubjectPublicKeyInfo) dans le dossier {@code ca} des données, lisibles par le seul propriétaire. Générée au premier
 * démarrage dans un dossier temporaire, puis mise en place par un déplacement atomique (d'après {@code DataDirectory}
 * de NIDO) : jamais de demi-clé, jamais d'écrasement. Un dossier {@code ca} vide, préparé par l'exploitant, compte
 * comme absent. Toute incohérence (fichier seul, paire qui ne va pas ensemble, droits plus larges que ceux du
 * propriétaire, lien symbolique) arrête le démarrage plutôt que de remplacer l'Autorité, ce qui bloquerait tous les
 * clients qui l'ont épinglée (KEY-11).
 */
@Component
public final class FileCaKeyStore implements AuthoritySigner {

    static final String PRIVATE_KEY_FILE = "seal-ca.p8";
    static final String PUBLIC_KEY_FILE = "seal-ca.pub";

    private static final Logger LOG = LoggerFactory.getLogger(FileCaKeyStore.class);

    /** Signature d'essai : une étiquette propre, qu'aucune structure signée de SEAL ne peut prendre. */
    private static final byte[] PROBE = Canonical.struct("SEAL-ca-probe-v1");

    private static final Set<PosixFilePermission> OWNER_ONLY_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY = PosixFilePermissions.fromString("rwx------");

    private final byte[] privateKey;
    private final AuthorityKey key;

    @Autowired
    public FileCaKeyStore(SealProperties seal) {
        this(seal.dataDir().resolve("ca"));
    }

    FileCaKeyStore(Path directory) {
        Path ca = directory.toAbsolutePath().normalize();
        refuseSymbolicLink(ca);
        boolean generated = false;
        if (!Files.exists(ca, LinkOption.NOFOLLOW_LINKS)) {
            generate(ca);
            generated = true;
        } else if (isEmptyDirectory(ca)) {
            // ADR 0025, point 12 : aucune Autorité n'a encore été écrite ; le dossier vide fait place à la paire.
            delete(ca);
            generate(ca);
            generated = true;
        }
        requireOwnerOnly(ca, OWNER_ONLY_DIRECTORY, "700");
        this.privateKey = read(ca, PRIVATE_KEY_FILE);
        this.key = new AuthorityKey(publicKey(ca));
        if (!Ed25519.verify(key.publicKey(), PROBE, signProbe())) {
            throw new IllegalStateException("Autorité SEAL : " + PRIVATE_KEY_FILE + " et " + PUBLIC_KEY_FILE
                    + " ne vont pas ensemble dans " + ca + " ; restaurez la sauvegarde du volume de données");
        }
        // KEY-10 : l'empreinte, publique, est journalisée ; jamais la clé privée (SEC-21).
        LOG.info("Autorité SEAL, empreinte SHA-256 : {}", key.fingerprint());
        if (generated) {
            // KEY-11, ADR 0025 (point 17) : attendue au premier démarrage, une nouvelle Autorité bloque après coup tous
            // les clients qui ont épinglé la précédente ; une restauration ratée ne doit pas passer inaperçue.
            LOG.warn(
                    "Nouvelle Autorité SEAL générée dans {}, empreinte SHA-256 : {} ; si une Autorité existait déjà,"
                            + " restaurez la sauvegarde du volume de données",
                    ca,
                    key.fingerprint());
        }
    }

    @Override
    public AuthorityKey key() {
        return key;
    }

    @Override
    public byte[] sign(byte[] message) {
        return Ed25519.sign(privateKey, message);
    }

    private byte[] signProbe() {
        try {
            return Ed25519.sign(privateKey, PROBE);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Autorité SEAL : " + PRIVATE_KEY_FILE + " illisible", e);
        }
    }

    private static byte[] publicKey(Path ca) {
        try {
            return Ed25519.fromSubjectPublicKeyInfo(read(ca, PUBLIC_KEY_FILE));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Autorité SEAL : " + PUBLIC_KEY_FILE + " illisible", e);
        }
    }

    private static byte[] read(Path ca, String name) {
        Path file = ca.resolve(name);
        refuseSymbolicLink(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Autorité SEAL incomplète : " + name + " manque dans " + ca
                    + " ; restaurez la sauvegarde du volume de données, jamais une nouvelle paire (KEY-10)");
        }
        requireOwnerOnly(file, OWNER_ONLY_FILE, "600");
        // Le fichier lui-même n'est jamais suivi s'il est un lien. Le dossier n'est contrôlé qu'au démarrage : un lien
        // posé entre-temps suppose l'uid du serveur, hors du modèle de menace (section 3 du CDC, ADR 0025 point 12).
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Autorité SEAL : lecture de " + file + " impossible", e);
        }
    }

    /** ADR 0025, point 12 : la paire vit dans le dossier {@code ca} lui-même, jamais derrière un lien. */
    private static void refuseSymbolicLink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new IllegalStateException("Autorité SEAL : " + path
                    + " est un lien symbolique ; placez la paire dans le dossier ca du volume de données");
        }
    }

    /** KEY-10 : dossier en 700 et fichiers en 600 au plus ; des droits plus étroits restent acceptés. */
    private static void requireOwnerOnly(Path path, Set<PosixFilePermission> allowed, String mode) {
        if (!posix()) {
            return;
        }
        try {
            if (!allowed.containsAll(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS))) {
                throw new IllegalStateException(
                        "Autorité SEAL : " + path + " doit être réservé à son seul propriétaire (droits " + mode + ")");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Autorité SEAL : droits de " + path + " illisibles", e);
        }
    }

    private static boolean isEmptyDirectory(Path ca) {
        if (!Files.isDirectory(ca, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (var entries = Files.list(ca)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            throw new IllegalStateException("Autorité SEAL : lecture de " + ca + " impossible", e);
        }
    }

    /** Un arrêt entre cette suppression et le déplacement laisse un dossier absent, que le démarrage suivant remplit. */
    private static void delete(Path ca) {
        try {
            Files.delete(ca);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Autorité SEAL : le dossier vide " + ca + " ne peut pas être remplacé par la paire générée", e);
        }
    }

    /** Paire écrite dans un dossier temporaire voisin, renommé d'un coup en {@code ca}. */
    private static void generate(Path ca) {
        try {
            Path parent = ca.getParent();
            if (parent == null) {
                throw new IllegalStateException("Autorité SEAL : seal.data-dir ne peut pas être la racine du disque");
            }
            Files.createDirectories(parent);
            Path staging = posix()
                    ? Files.createTempDirectory(
                            parent, ca.getFileName() + ".", PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY))
                    : Files.createTempDirectory(parent, ca.getFileName() + ".");
            try {
                Ed25519.GeneratedKey generated = Ed25519.generate();
                write(staging.resolve(PRIVATE_KEY_FILE), generated.privateKey());
                write(staging.resolve(PUBLIC_KEY_FILE), Ed25519.toSubjectPublicKeyInfo(generated.publicKey()));
                Files.move(staging, ca, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | RuntimeException e) {
                // KEY-10 : une génération perdue (démarrage concurrent, déplacement refusé) ne laisse aucune clé privée
                // orpheline dans le volume ni dans ses sauvegardes.
                discard(staging, e);
                throw e;
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Autorité SEAL : impossible d'écrire à côté de " + ca
                            + " ; seal.data-dir doit être un dossier inscriptible qui survit aux redémarrages",
                    e);
        }
    }

    private static void discard(Path staging, Exception failure) {
        try {
            Files.deleteIfExists(staging.resolve(PRIVATE_KEY_FILE));
            Files.deleteIfExists(staging.resolve(PUBLIC_KEY_FILE));
            Files.deleteIfExists(staging);
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private static void write(Path file, byte[] content) throws IOException {
        FileAttribute<?>[] attributes = posix()
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE)}
                : new FileAttribute<?>[0];
        Files.write(Files.createFile(file, attributes), content);
    }

    private static boolean posix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }
}
