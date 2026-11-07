package fr.vlegall.seal;

import org.springframework.boot.SpringApplication;

/**
 * Lance SEAL en développement avec un PostgreSQL jetable : {@code ./mvnw spring-boot:test-run}, depuis {@code src/server}.
 * Le profil demo s'applique, donc TLS : les certificats de {@code src/certs} doivent exister ({@code scripts/gen-certs.sh}).
 */
public class TestSealApplication {

    public static void main(String[] args) {
        SpringApplication.from(SealApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
