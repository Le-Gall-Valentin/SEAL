package fr.vlegall.seal;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.support.SealIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Règles valables pour tout le schéma, vérifiées sur la base migrée plutôt que sur les migrations (ADR 0009). Une
 * règle d'ici vaut aussi pour les tables à venir : la prochaine en hérite au lieu de réintroduire un défaut corrigé.
 */
@SealIntegrationTest
class SchemaConventionsIT {

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * PostgreSQL indexe le côté référencé d'une clé étrangère, jamais le côté qui référence : sans index, supprimer un
     * compte parcourt toute la table enfant. L'index doit commencer par la colonne et ne pas être partiel, sinon il ne
     * répond pas à « une ligne référence-t-elle encore ce parent ? ».
     */
    @Test
    @DisplayName("DATA-01 : chaque clé étrangère a un index complet qui commence par sa colonne (ADR 0009)")
    void data01EveryForeignKeyHasAnIndexLeadingWithItsColumn() {
        List<String> unindexed = jdbc.queryForList("""
                SELECT c.conrelid::regclass || '.' || a.attname
                FROM pg_constraint c
                JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
                WHERE c.contype = 'f'
                  AND array_length(c.conkey, 1) = 1
                  AND NOT EXISTS (
                        SELECT 1 FROM pg_index i
                        WHERE i.indrelid = c.conrelid
                          AND i.indkey[0] = c.conkey[1]
                          AND i.indpred IS NULL)
                ORDER BY 1
                """, String.class);

        assertThat(unindexed)
                .as("chacune de ces clés fait parcourir toute sa table à la suppression du parent")
                .isEmpty();
    }

    @Test
    @DisplayName("DATA-01 : la règle des index voit bien les clés étrangères du schéma")
    void data01ForeignKeyRuleIsLookingAtSomething() {
        Integer singleColumnForeignKeys = jdbc.queryForObject("""
                SELECT count(*)::int FROM pg_constraint
                WHERE contype = 'f' AND array_length(conkey, 1) = 1
                """, Integer.class);

        assertThat(singleColumnForeignKeys).isGreaterThanOrEqualTo(20);
    }
}
