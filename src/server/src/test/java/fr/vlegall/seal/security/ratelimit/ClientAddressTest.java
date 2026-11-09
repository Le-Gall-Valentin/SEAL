package fr.vlegall.seal.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientAddressTest {

    @Test
    @DisplayName("SEC-10 : une adresse IPv4 reste entière")
    void sec10Ipv4IsKeptWhole() {
        assertThat(ClientAddress.key("203.0.113.9")).isEqualTo("203.0.113.9");
        assertThat(ClientAddress.key("203.0.113.10")).isNotEqualTo(ClientAddress.key("203.0.113.9"));
    }

    @Test
    @DisplayName(
            "SEC-10 : deux adresses IPv6 d'un même /64 partagent leur clé, quelle que soit leur écriture (ADR 0021)")
    void sec10Ipv6IsAggregatedToItsSlash64() {
        String key = ClientAddress.key("2001:db8:1:2:3:4:5:6");

        assertThat(ClientAddress.key("2001:db8:1:2::9")).isEqualTo(key);
        assertThat(ClientAddress.key("2001:0DB8:0001:0002:ffff:ffff:ffff:ffff")).isEqualTo(key);
        assertThat(ClientAddress.key("fe80::1%eth0")).isEqualTo(ClientAddress.key("fe80::2"));
        assertThat(ClientAddress.key("2001:db8:1:3::1")).isNotEqualTo(key);
        assertThat(key).isEqualTo("2001:db8:1:2::/64");
    }

    @Test
    @DisplayName("SEC-10 : une adresse IPv4 mappée en IPv6 garde la clé de l'adresse IPv4")
    void sec10MappedIpv4KeepsItsIpv4Key() {
        assertThat(ClientAddress.key("::ffff:203.0.113.9")).isEqualTo("203.0.113.9");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "localhost", "pas une adresse", "999.1.1.1", "2001:db8::g"})
    @DisplayName("SEC-10 : une adresse illisible, jamais résolue par DNS, partage une clé unique (échec fermé)")
    void sec10UnreadableAddressSharesOneKey(String raw) {
        assertThat(ClientAddress.key(raw)).isEqualTo(ClientAddress.UNREADABLE);
    }
}
