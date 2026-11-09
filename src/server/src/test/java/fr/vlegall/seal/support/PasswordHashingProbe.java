package fr.vlegall.seal.support;

import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Observe l'état de la base pendant chaque hachage Argon2id des tests d'intégration (ADR 0019, point 3) : une connexion
 * liée au fil, ou empruntée au pool, serait tenue pendant tout le calcul et l'attente de son permis.
 */
public final class PasswordHashingProbe {

    /**
     * @param resourceBound une ressource (connexion, gestionnaire d'entités) est liée au fil
     * @param activeConnections connexions empruntées au pool Hikari
     */
    public record Observation(boolean resourceBound, int activeConnections) {}

    private final ObjectProvider<DataSource> dataSource;
    private final List<Observation> observations = new CopyOnWriteArrayList<>();

    PasswordHashingProbe(ObjectProvider<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    void observe() {
        DataSource source = dataSource.getObject();
        int active = source instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null
                ? hikari.getHikariPoolMXBean().getActiveConnections()
                : -1;
        observations.add(new Observation(
                !TransactionSynchronizationManager.getResourceMap().isEmpty(), active));
    }

    /** Observations faites depuis le dernier {@link #reset()}, dans l'ordre des hachages. */
    public List<Observation> observations() {
        return List.copyOf(observations);
    }

    public void reset() {
        observations.clear();
    }
}
