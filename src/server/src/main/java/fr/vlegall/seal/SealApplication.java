package fr.vlegall.seal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Point d'entrée du serveur SEAL : il authentifie, certifie les clés et relaie des enveloppes qu'il ne peut pas lire. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SealApplication {

    public static void main(String[] args) {
        SpringApplication.run(SealApplication.class, args);
    }
}
