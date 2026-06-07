package com.example.aihub.common.security;

import com.example.aihub.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * SSRF 防护：在服务端对用户可控的 URL 发起请求前，校验目标主机不指向内网 / 保留网段 / 云元数据地址。
 */
@Component
public final class SsrfGuard {
    private static volatile boolean allowLoopbackTargets;

    @Value("${app.security.allow-loopback-targets:false}")
    void setAllowLoopbackTargets(boolean allowLoopbackTargets) {
        SsrfGuard.allowLoopbackTargets = allowLoopbackTargets;
    }

    public SsrfGuard() {
    }

    /** 校验 URL 的协议与目标主机是否安全，不安全则抛出业务异常。 */
    public static void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException("请求地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception ex) {
            throw new BusinessException("请求地址格式非法");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BusinessException("仅支持 http/https 协议的地址");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException("请求地址缺少主机名");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new BusinessException("无法解析目标主机");
        }
        for (InetAddress addr : addresses) {
            if (isBlocked(addr)) {
                throw new BusinessException("出于安全考虑，禁止访问内网或保留地址");
            }
        }
    }

    private static boolean isBlocked(InetAddress addr) {
        if (addr.isAnyLocalAddress()      // 0.0.0.0 / ::
                || (!allowLoopbackTargets && addr.isLoopbackAddress()) // localhost / 127.0.0.1 / ::1
                || addr.isLinkLocalAddress()   // 169.254.0.0/16（含云元数据 169.254.169.254）/ fe80::/10
                || addr.isSiteLocalAddress()   // 10/8, 172.16/12, 192.168/16
                || addr.isMulticastAddress()) {
            return true;
        }
        return false;
    }
}
