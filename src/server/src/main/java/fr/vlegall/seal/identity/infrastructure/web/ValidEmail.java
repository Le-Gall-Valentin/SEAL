package fr.vlegall.seal.identity.infrastructure.web;

import fr.vlegall.seal.identity.domain.model.EmailAddress;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.jspecify.annotations.Nullable;

/** SEC-01 : courriel au format d'{@link EmailAddress}, la règle n'étant écrite qu'une fois (d'après NIDO). */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = ValidEmail.Validator.class)
public @interface ValidEmail {

    String message() default "courriel invalide";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Une valeur absente relève de {@code @NotNull}. */
    final class Validator implements ConstraintValidator<ValidEmail, String> {

        @Override
        public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
            return value == null || EmailAddress.parse(value).isPresent();
        }
    }
}
