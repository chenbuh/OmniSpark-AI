package com.example.aihub.infrastructure.service;

import com.example.aihub.infrastructure.vo.IpGeoInfoVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IpGeoLookupService {
    private static final String SOURCE_LOCAL = "local";
    private static final String SOURCE_IPWHOIS = "ipwho.is";
    private static final String SOURCE_DISABLED = "disabled";
    private static final String SOURCE_UNKNOWN = "unknown";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Value("${app.security.ip-geo.enabled:true}")
    private boolean enabled;

    @Value("${app.security.ip-geo.base-url:https://ipwho.is}")
    private String baseUrl;

    @Value("${app.security.ip-geo.timeout-ms:3000}")
    private int timeoutMs;

    @Value("${app.security.ip-geo.cache-minutes:720}")
    private long cacheMinutes;

    public IpGeoLookupService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    public Map<String, IpGeoInfoVO> resolveBatch(Collection<String> ips) {
        Map<String, IpGeoInfoVO> result = new LinkedHashMap<>();
        if (ips == null) {
            return result;
        }
        for (String ip : ips) {
            String normalized = normalize(ip);
            if (normalized.isBlank() || result.containsKey(normalized)) {
                continue;
            }
            result.put(normalized, resolve(normalized));
        }
        return result;
    }

    public IpGeoInfoVO resolve(String ip) {
        String normalized = normalize(ip);
        if (normalized.isBlank()) {
            return buildUnknown(ip, "IP 待确认");
        }
        CacheEntry cached = cache.get(normalized);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMs >= now) {
            return cached.value;
        }
        IpGeoInfoVO resolved = lookup(normalized);
        cache.put(normalized, new CacheEntry(resolved, now + Math.max(1, cacheMinutes) * 60_000));
        return resolved;
    }

    private IpGeoInfoVO lookup(String ip) {
        InetAddress address = parseIp(ip);
        if (address == null) {
            return buildUnknown(ip, "IP 格式无效");
        }
        if (!isPublicRoutable(address)) {
            return buildLocal(ip, describeReservedAddress(address));
        }
        if (!enabled) {
            return buildUnknown(ip, "IP 归属查询已关闭", SOURCE_DISABLED);
        }
        try {
            String fields = String.join(",",
                    "success",
                    "message",
                    "continent",
                    "country",
                    "region",
                    "city",
                    "postal",
                    "latitude",
                    "longitude",
                    "timezone.id",
                    "timezone.abbr",
                    "timezone.utc",
                    "connection.isp",
                    "connection.org",
                    "connection.asn",
                    "security.proxy",
                    "security.vpn",
                    "security.tor",
                    "security.hosting"
            );
            URI uri = URI.create(baseUrl
                    + "/"
                    + URLEncoder.encode(ip, StandardCharsets.UTF_8)
                    + "?lang=zh-CN&fields="
                    + URLEncoder.encode(fields, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(Math.max(1000, timeoutMs)))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return buildUnknown(ip, "IP 归属查询服务响应异常: HTTP " + response.statusCode(), SOURCE_IPWHOIS);
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                String message = root.path("message").asText("").trim();
                return buildUnknown(ip, message.isBlank() ? "IP 归属查询失败" : message, SOURCE_IPWHOIS);
            }
            return buildRemote(ip, root);
        } catch (Exception ex) {
            return buildUnknown(ip, "IP 归属查询失败: " + safeMessage(ex), SOURCE_IPWHOIS);
        }
    }

    private IpGeoInfoVO buildRemote(String ip, JsonNode root) {
        IpGeoInfoVO info = new IpGeoInfoVO();
        info.setIp(ip);
        info.setSource(SOURCE_IPWHOIS);
        info.setPrivateNetwork(false);
        info.setContinent(text(root, "continent"));
        info.setCountry(text(root, "country"));
        info.setRegion(text(root, "region"));
        info.setCity(text(root, "city"));
        info.setPostalCode(text(root, "postal"));
        JsonNode timezone = root.path("timezone");
        info.setTimezoneId(text(timezone, "id"));
        info.setTimezoneAbbr(text(timezone, "abbr"));
        info.setTimezoneUtc(text(timezone, "utc"));
        info.setLatitude(number(root, "latitude"));
        info.setLongitude(number(root, "longitude"));
        JsonNode connection = root.path("connection");
        info.setIsp(text(connection, "isp"));
        info.setOrganization(text(connection, "org"));
        Long asn = longNumber(connection, "asn");
        info.setAsn(asn != null && asn > 0 ? asn : null);
        JsonNode security = root.path("security");
        info.setProxy(booleanValue(security, "proxy"));
        info.setVpn(booleanValue(security, "vpn"));
        info.setTor(booleanValue(security, "tor"));
        info.setHosting(booleanValue(security, "hosting"));
        info.setLocationSummary(buildLocationSummary(info));
        info.setDetailMessage(buildDetailMessage(info));
        return info;
    }

    private IpGeoInfoVO buildLocal(String ip, String message) {
        IpGeoInfoVO info = new IpGeoInfoVO();
        info.setIp(ip);
        info.setSource(SOURCE_LOCAL);
        info.setPrivateNetwork(true);
        info.setCountry("本地网络");
        info.setRegion(message);
        info.setLocationSummary(message);
        info.setDetailMessage(message);
        info.setProxy(false);
        info.setVpn(false);
        info.setTor(false);
        info.setHosting(false);
        return info;
    }

    private IpGeoInfoVO buildUnknown(String ip, String message) {
        return buildUnknown(ip, message, SOURCE_UNKNOWN);
    }

    private IpGeoInfoVO buildUnknown(String ip, String message, String source) {
        IpGeoInfoVO info = new IpGeoInfoVO();
        info.setIp(ip == null ? "" : ip);
        info.setSource(source);
        info.setLocationSummary("待确认");
        info.setDetailMessage(message == null || message.isBlank() ? "IP 归属待确认" : message);
        return info;
    }

    private String buildLocationSummary(IpGeoInfoVO info) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, info.getCountry());
        appendPart(builder, info.getRegion());
        appendPart(builder, info.getCity());
        if (builder.length() == 0) {
            return "待确认";
        }
        return builder.toString();
    }

    private String buildDetailMessage(IpGeoInfoVO info) {
        StringBuilder builder = new StringBuilder();
        appendLabeled(builder, "运营商", info.getIsp());
        appendLabeled(builder, "组织", info.getOrganization());
        appendLabeled(builder, "时区", info.getTimezoneId());
        if (info.getLatitude() != null && info.getLongitude() != null) {
            appendLabeled(builder, "坐标", String.format(Locale.ROOT, "%.4f, %.4f", info.getLatitude(), info.getLongitude()));
        }
        String flags = buildSecurityFlags(info);
        if (!flags.isBlank()) {
            appendLabeled(builder, "网络特征", flags);
        }
        return builder.length() == 0 ? info.getLocationSummary() : builder.toString();
    }

    private String buildSecurityFlags(IpGeoInfoVO info) {
        StringBuilder builder = new StringBuilder();
        appendFlag(builder, info.getProxy(), "代理");
        appendFlag(builder, info.getVpn(), "VPN");
        appendFlag(builder, info.getTor(), "Tor");
        appendFlag(builder, info.getHosting(), "机房");
        return builder.toString();
    }

    private void appendFlag(StringBuilder builder, Boolean enabledFlag, String label) {
        if (!Boolean.TRUE.equals(enabledFlag)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" / ");
        }
        builder.append(label);
    }

    private void appendPart(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" / ");
        }
        builder.append(value.trim());
    }

    private void appendLabeled(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("；");
        }
        builder.append(label).append(": ").append(value.trim());
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        String value = node.path(fieldName).asText("").trim();
        return "null".equalsIgnoreCase(value) ? "" : value;
    }

    private Double number(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.has(fieldName) || node.path(fieldName).isNull()) {
            return null;
        }
        return node.path(fieldName).isNumber() ? node.path(fieldName).asDouble() : null;
    }

    private Long longNumber(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.has(fieldName) || node.path(fieldName).isNull()) {
            return null;
        }
        return node.path(fieldName).isNumber() ? node.path(fieldName).asLong() : null;
    }

    private Boolean booleanValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.has(fieldName) || node.path(fieldName).isNull()) {
            return null;
        }
        return node.path(fieldName).asBoolean();
    }

    private InetAddress parseIp(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isPublicRoutable(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address ipv4) {
            byte[] bytes = ipv4.getAddress();
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            if (first == 0 || first >= 224) {
                return false;
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return false;
            }
            if (first == 169 && second == 254) {
                return false;
            }
            if (first == 198 && (second == 18 || second == 19)) {
                return false;
            }
            return true;
        }
        if (address instanceof Inet6Address ipv6) {
            byte[] bytes = ipv6.getAddress();
            int first = bytes[0] & 0xFF;
            if ((first & 0xFE) == 0xFC) {
                return false;
            }
            if (first == 0x20 && (bytes[1] & 0xFF) == 0x01 && (bytes[2] & 0xFF) == 0x0D && (bytes[3] & 0xFF) == 0xB8) {
                return false;
            }
            return true;
        }
        return true;
    }

    private String describeReservedAddress(InetAddress address) {
        if (address.isLoopbackAddress()) {
            return "本机回环地址";
        }
        if (address.isSiteLocalAddress()) {
            return "内网地址";
        }
        if (address.isLinkLocalAddress()) {
            return "链路本地地址";
        }
        if (address.isAnyLocalAddress()) {
            return "未指定地址";
        }
        if (address.isMulticastAddress()) {
            return "多播地址";
        }
        return "保留地址";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message.trim();
    }

    private record CacheEntry(IpGeoInfoVO value, long expiresAtMs) {
    }
}
