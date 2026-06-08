package com.example.aihub.common.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@Component
public class ClientIpResolver {
    @Value("${app.security.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,100.64.0.0/10}")
    private String trustedProxiesConfig;

    private final List<TrustedNetwork> trustedNetworks = new ArrayList<>();

    @PostConstruct
    public void init() {
        trustedNetworks.clear();
        if (trustedProxiesConfig == null || trustedProxiesConfig.isBlank()) {
            return;
        }
        for (String item : trustedProxiesConfig.split(",")) {
            TrustedNetwork network = TrustedNetwork.parse(item.trim());
            if (network != null) {
                trustedNetworks.add(network);
            }
        }
    }

    public String resolve(HttpServletRequest request) {
        Object cached = request.getAttribute(SecurityRequestAttributes.CLIENT_IP);
        if (cached instanceof String value && !value.isBlank()) {
            return value;
        }

        String remoteAddr = normalize(request.getRemoteAddr());
        String clientIp = remoteAddr;
        if (isTrustedProxy(remoteAddr)) {
            String forwardedIp = resolveForwardedClientIp(request);
            if (forwardedIp != null) {
                clientIp = forwardedIp;
            }
        }

        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "unknown";
        }
        request.setAttribute(SecurityRequestAttributes.CLIENT_IP, clientIp);
        return clientIp;
    }

    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String normalized = normalize(ip);
        for (TrustedNetwork network : trustedNetworks) {
            if (network.matches(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String resolveForwardedClientIp(HttpServletRequest request) {
        String[] headerCandidates = {
                firstForwardedHeaderIp(request.getHeader("Forwarded")),
                firstIp(request.getHeader("CF-Connecting-IP")),
                firstIp(request.getHeader("True-Client-IP")),
                firstIp(request.getHeader("X-Real-IP")),
                firstIp(request.getHeader("X-Forwarded-For")),
                firstIp(request.getHeader("X-Original-Forwarded-For")),
                firstIp(request.getHeader("Proxy-Client-IP")),
                firstIp(request.getHeader("WL-Proxy-Client-IP")),
                firstIp(request.getHeader("HTTP_X_FORWARDED_FOR")),
                firstIp(request.getHeader("HTTP_CLIENT_IP"))
        };
        for (String candidate : headerCandidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private String firstForwardedHeaderIp(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String[] entries = header.split(",");
        for (String entry : entries) {
            String[] directives = entry.split(";");
            for (String directive : directives) {
                String trimmed = directive.trim();
                if (trimmed.length() < 4 || !trimmed.regionMatches(true, 0, "for=", 0, 4)) {
                    continue;
                }
                String ip = normalize(trimmed.substring(4));
                if (isUsableIp(ip)) {
                    return ip;
                }
            }
        }
        return null;
    }

    private String firstIp(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String[] parts = header.split(",");
        for (String part : parts) {
            String ip = normalize(part);
            if (isUsableIp(ip)) {
                return ip;
            }
        }
        return null;
    }

    private boolean isUsableIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String normalized = ip.trim();
        if ("unknown".equalsIgnoreCase(normalized) || "_hidden".equalsIgnoreCase(normalized)) {
            return false;
        }
        try {
            InetAddress.getByName(normalized);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalize(String ip) {
        if (ip == null) {
            return "";
        }
        String value = ip.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.startsWith("for=") || value.startsWith("For=")) {
            value = value.substring(4).trim();
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        int bracketEnd = value.indexOf(']');
        if (value.startsWith("[") && bracketEnd > 0) {
            value = value.substring(1, bracketEnd);
        } else if (value.chars().filter(ch -> ch == ':').count() == 1) {
            int portIndex = value.lastIndexOf(':');
            String maybeIpv4 = value.substring(0, portIndex);
            if (maybeIpv4.indexOf('.') >= 0) {
                value = maybeIpv4;
            }
        }
        if (value.startsWith("::ffff:")) {
            value = value.substring("::ffff:".length());
        }
        if (value.indexOf('%') > 0) {
            value = value.substring(0, value.indexOf('%'));
        }
        value = value.trim();
        if ("unknown".equalsIgnoreCase(value) || "_hidden".equalsIgnoreCase(value)) {
            return "";
        }
        return value;
    }

    private record TrustedNetwork(byte[] address, int prefixBits) {
        private static TrustedNetwork parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                String ip = value;
                int prefix = -1;
                int slash = value.indexOf('/');
                if (slash > 0) {
                    ip = value.substring(0, slash);
                    prefix = Integer.parseInt(value.substring(slash + 1));
                }
                byte[] bytes = InetAddress.getByName(ip).getAddress();
                int maxBits = bytes.length * 8;
                if (prefix < 0) {
                    prefix = maxBits;
                }
                if (prefix < 0 || prefix > maxBits) {
                    return null;
                }
                return new TrustedNetwork(bytes, prefix);
            } catch (Exception ignored) {
                return null;
            }
        }

        private boolean matches(String value) {
            try {
                byte[] candidate = InetAddress.getByName(value).getAddress();
                if (candidate.length != address.length) {
                    return false;
                }
                int fullBytes = prefixBits / 8;
                int remainingBits = prefixBits % 8;
                for (int i = 0; i < fullBytes; i++) {
                    if (candidate[i] != address[i]) {
                        return false;
                    }
                }
                if (remainingBits == 0) {
                    return true;
                }
                int mask = 0xFF << (8 - remainingBits);
                return (candidate[fullBytes] & mask) == (address[fullBytes] & mask);
            } catch (Exception ignored) {
                return false;
            }
        }
    }
}
