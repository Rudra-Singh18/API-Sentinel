package dev.apisentinel.security;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UrlSafetyPolicy {
    private final boolean allowHttp;

    public UrlSafetyPolicy(@Value("${sentinel.allow-http:false}") boolean allowHttp) {
        this.allowHttp = allowHttp;
    }

    public URI validate(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !(allowHttp && scheme.equals("http"))) reject("Only HTTPS monitor URLs are allowed.");
            if (uri.getUserInfo() != null || uri.getFragment() != null) reject("URL credentials and fragments are not allowed.");
            String rawHost = uri.getHost();
            if (rawHost == null || rawHost.isBlank()) reject("A valid hostname is required.");
            String host = rawHost.replace("[", "").replace("]", "");
            if (host.length() > 253 || host.endsWith(".")) reject("Invalid hostname.");
            int port = uri.getPort();
            if (scheme.equals("https") && port != -1 && port != 443) reject("Only HTTPS port 443 is allowed.");
            if (scheme.equals("http") && (!allowHttp || (port != -1 && port != 80))) reject("Only HTTP port 80 is allowed when HTTP is enabled.");
            String asciiHost = host.contains(":") ? host : IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (isBlockedName(asciiHost)) reject("Private and local network destinations are not allowed.");
            for (InetAddress address : resolve(asciiHost)) {
                if (!isPublicAddress(address)) reject("Private and non-public network destinations are not allowed.");
            }
            return uri;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IllegalArgumentException | UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The monitor URL is invalid or its hostname could not be resolved.");
        }
    }

    private List<InetAddress> resolve(String host) throws UnknownHostException {
        return List.of(InetAddress.getAllByName(host));
    }

    private boolean isBlockedName(String host) {
        return host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal") || host.equals("metadata") || host.equals("metadata.google.internal")
                || host.equals("instance-data.ec2.internal");
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) return isPublicIpv4(bytes);
        if (address instanceof Inet6Address) return isPublicIpv6(bytes);
        return false;
    }

    private boolean isPublicIpv4(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);
        if (first == 0 || first == 10 || first == 127 || first >= 224) return false;
        if (first == 100 && second >= 64 && second <= 127) return false;
        if (first == 169 && second == 254) return false;
        if (first == 172 && second >= 16 && second <= 31) return false;
        if (first == 192 && (second == 0 || second == 168)) return false;
        if (first == 198 && (second == 18 || second == 19 || second == 51)) return false;
        if (first == 203 && second == 0 && third == 113) return false;
        return true;
    }

    private boolean isPublicIpv6(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        boolean globalUnicast = (first & 0xE0) == 0x20;
        boolean documentation = first == 0x20 && second == 0x01 && Byte.toUnsignedInt(bytes[2]) == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8;
        boolean sixToFour = first == 0x20 && second == 0x02;
        return globalUnicast && !documentation && !sixToFour;
    }

    private void reject(String reason) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}