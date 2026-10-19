package dev.apisentinel.security;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class UrlSafetyPolicyTest {
    private final UrlSafetyPolicy policy = new UrlSafetyPolicy(false);

    @Test
    void rejectsLoopbackAndPrivateIpv4Addresses() {
        assertThrows(ResponseStatusException.class, () -> policy.validate("https://127.0.0.1/health"));
        assertThrows(ResponseStatusException.class, () -> policy.validate("https://10.1.2.3/health"));
        assertThrows(ResponseStatusException.class, () -> policy.validate("https://192.168.1.5/health"));
    }

    @Test
    void rejectsMetadataLinkLocalAndIpv6LocalAddresses() {
        assertThrows(ResponseStatusException.class, () -> policy.validate("https://169.254.169.254/latest/meta-data/"));
        assertThrows(ResponseStatusException.class, () -> policy.validate("https://[::1]/health"));
        assertThrows(ResponseStatusException.class, () -> policy.validate("https://[fc00::1]/health"));
    }

    @Test
    void rejectsCredentialsNonHttpsAndUnusualPorts() {
        assertThrows(ResponseStatusException.class, () -> policy.validate("https://user:pass@example.com/health"));
        assertThrows(ResponseStatusException.class, () -> policy.validate("http://example.com/health"));
        assertThrows(ResponseStatusException.class, () -> policy.validate("https://example.com:8443/health"));
    }

    @Test
    void rejectsLocalAndMetadataHostnamesBeforeResolution() {
        assertThrows(ResponseStatusException.class, () -> policy.validate("https://localhost/health"));
        assertThrows(ResponseStatusException.class, () -> policy.validate("https://metadata.google.internal/"));
    }
}