package fr.vlegall.seal.support;

import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Enveloppe le {@link PasswordHasher} réel : chaque hachage passe par {@link PasswordHashingProbe}, puis par lui. */
@TestConfiguration(proxyBeanMethods = false)
public class PasswordHashingProbeConfiguration {

    @Bean
    static PasswordHashingProbe passwordHashingProbe(ObjectProvider<DataSource> dataSource) {
        return new PasswordHashingProbe(dataSource);
    }

    @Bean
    static BeanPostProcessor passwordHasherObserver(ObjectProvider<PasswordHashingProbe> probe) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (!(bean instanceof PasswordHasher hasher)) {
                    return bean;
                }
                return new PasswordHasher() {
                    @Override
                    public String hash(Password password) {
                        probe.getObject().observe();
                        return hasher.hash(password);
                    }

                    @Override
                    public boolean matches(Password password, String encoded) {
                        probe.getObject().observe();
                        return hasher.matches(password, encoded);
                    }

                    @Override
                    public boolean recognises(String encoded) {
                        return hasher.recognises(encoded);
                    }
                };
            }
        };
    }
}
