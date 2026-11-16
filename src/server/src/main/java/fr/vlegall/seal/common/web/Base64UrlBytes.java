package fr.vlegall.seal.common.web;

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

/**
 * SEC-01 : chaîne base64url sans remplissage, sous sa forme canonique, de {@code min} à {@code max} octets une fois
 * décodée (PROTOCOL.md, section 1). Une valeur absente relève de {@code @NotNull}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = Base64UrlBytes.Validator.class)
public @interface Base64UrlBytes {

    String message() default "octets base64url invalides";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Nombre minimal d'octets décodés. */
    int min() default 0;

    /** Nombre maximal d'octets décodés. */
    int max();

    final class Validator implements ConstraintValidator<Base64UrlBytes, String> {

        private int min;
        private int max;

        @Override
        public void initialize(Base64UrlBytes constraint) {
            this.min = constraint.min();
            this.max = constraint.max();
        }

        @Override
        public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            // Borne avant tout décodage : une chaîne démesurée n'est jamais décodée (A06).
            if (value.length() > (max + 2) / 3 * 4) {
                return false;
            }
            return Base64Url.parse(value)
                    .map(bytes -> bytes.length >= min && bytes.length <= max)
                    .orElse(false);
        }
    }
}
