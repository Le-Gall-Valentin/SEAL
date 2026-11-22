package fr.vlegall.seal.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class SudoModeTest {

    private static final Instant NOW = Instant.parse("2026-11-21T15:00:00Z");
    private static final UserId USER = new UserId(UUID.fromString("0190f1d2-7e3a-7b4c-8d5e-6f7a8b9c0d1e"));

    private final MutableClock clock = MutableClock.fixedAt(NOW);
    private final SudoMode sudo = new SudoMode(clock, Duration.ofMinutes(10));

    private static GrantedAuthority factor(String authority, Instant issuedAt) {
        return FactorGrantedAuthority.withAuthority(authority)
                .issuedAt(issuedAt)
                .build();
    }

    private static Authentication session(GrantedAuthority... factors) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(SealAuthorities.ACCOUNT_ACTIVE));
        authorities.addAll(List.of(factors));
        return UsernamePasswordAuthenticationToken.authenticated(new SealPrincipal(USER, 1L), null, authorities);
    }

    @Test
    @DisplayName("AUTH-55 : un facteur obtenu il y a moins de 10 minutes ouvre le mode sudo")
    void auth55RecentFactorOpensSudo() {
        Authentication authentication = session(factor(
                SealAuthorities.FACTOR_PASSWORD,
                NOW.minus(Duration.ofMinutes(10)).plusSeconds(1)));

        assertThat(sudo.isActive(authentication)).isTrue();
        sudo.require(authentication);
    }

    @Test
    @DisplayName("AUTH-55 : à 10 minutes pile, ou plus, le facteur ne suffit plus : 403 REAUTH_REQUIRED")
    void auth55FactorOfTenMinutesOrMoreIsTooOld() {
        for (Duration age : List.of(Duration.ofMinutes(10), Duration.ofHours(2))) {
            Authentication authentication = session(factor(SealAuthorities.FACTOR_PASSWORD, NOW.minus(age)));

            assertThat(sudo.isActive(authentication)).as(age.toString()).isFalse();
            assertThatThrownBy(() -> sudo.require(authentication))
                    .isInstanceOfSatisfying(
                            SealException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REAUTH_REQUIRED));
        }
    }

    @Test
    @DisplayName("AUTH-55, ADR 0034 : l'un ou l'autre facteur suffit, mot de passe ou retour OAuth")
    void auth55AnyRecentFactorIsEnough() {
        Instant old = NOW.minus(Duration.ofHours(1));
        Instant recent = NOW.minus(Duration.ofMinutes(2));

        assertThat(sudo.isActive(session(
                        factor(SealAuthorities.FACTOR_PASSWORD, old),
                        factor(SealAuthorities.FACTOR_AUTHORIZATION_CODE, recent))))
                .isTrue();
        assertThat(sudo.isActive(session(factor(SealAuthorities.FACTOR_AUTHORIZATION_CODE, recent))))
                .isTrue();
    }

    @Test
    @DisplayName("AUTH-55 : sans facteur daté ni authentification, pas de mode sudo (échec fermé)")
    void auth55NoFactorNoSudo() {
        assertThat(sudo.isActive(session())).isFalse();
        assertThat(sudo.isActive(null)).isFalse();
        // Une autorité qui porte le nom d'un facteur sans en avoir la date ne compte pas.
        assertThat(sudo.isActive(session(new SimpleGrantedAuthority(SealAuthorities.FACTOR_PASSWORD))))
                .isFalse();
        assertThatThrownBy(() -> sudo.require(null))
                .isInstanceOfSatisfying(
                        SealException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REAUTH_REQUIRED));
    }
}
