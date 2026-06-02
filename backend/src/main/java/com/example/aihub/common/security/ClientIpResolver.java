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
    @Value("${app.security.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}")
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
            String realIp = firstIp(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                clientIp = realIp;
            } else {
                String forwarded = firstIp(request.getHeader("X-Forwarded-For"));
                if (forwarded != null) {
                    clientIp = forwarded;
                }
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

    private String firstIp(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String[] parts = header.split(",");
        for (String part : parts) {
            String ip = normalize(part);
            if (!ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
        }
        return null;
    }

    private String normalize(String ip) {
        if (ip == null) {
            return "";
        }
        String value = ip.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.startsWith("::ffff:")) {
            value = value.substring("::ffff:".length());
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
