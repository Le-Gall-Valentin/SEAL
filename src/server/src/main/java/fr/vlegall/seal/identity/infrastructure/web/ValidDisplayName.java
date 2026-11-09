package fr.vlegall.seal.identity.infrastructure.web;

import fr.vlegall.seal.identity.domain.model.DisplayName;
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

/** AUTH-10, SEC-01 : nom affiché au format de {@link DisplayName}, la règle n'étant écrite qu'une fois. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = ValidDisplayName.Validator.class)
public @interface ValidDisplayName {

    String message() default "nom affiché invalide";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Une valeur absente relève de {@code @NotNull}. */
    final class Validator implements ConstraintValidator<ValidDisplayName, String> {

        @Override
        public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
            return value == null || DisplayName.parse(value).isPresent();
        }
    }
}
