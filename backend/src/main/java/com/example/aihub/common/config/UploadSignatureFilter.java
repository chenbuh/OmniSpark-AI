package com.example.aihub.common.config;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.security.ProjectAccessGuard;
import com.example.aihub.common.security.SecurityRequestAttributes;
import com.example.aihub.common.security.UploadAccessSignatureService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
public class UploadSignatureFilter implements Filter {
    private final UploadAccessSignatureService uploadAccessSignatureService;
    private final ProjectAccessGuard projectAccessGuard;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getRequestURI();
        if (path == null || !path.startsWith("/uploads/")) {
            chain.doFilter(request, response);
            return;
        }

        String expiresAt = req.getParameter("exp");
        String signature = req.getParameter("sig");
        String mode = req.getParameter("mode");
        String projectId = req.getParameter("pid");
        String userId = req.getParameter("uid");
        var access = uploadAccessSignatureService.verify(path, expiresAt, signature, mode, projectId, userId);
        if (!access.valid()) {
            writeForbidden((HttpServletResponse) response);
            return;
        }

        Long currentLoginUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        if (access.userId() != null) {
            if (currentLoginUserId != null && !access.userId().equals(currentLoginUserId)) {
                writeForbidden((HttpServletResponse) response);
                return;
            }
            if (UploadAccessSignatureService.MODE_PROJECT.equals(access.mode())
                    && (access.projectId() == null || !projectAccessGuard.canUserAccess(access.userId(), access.projectId()))) {
                writeForbidden((HttpServletResponse) response);
                return;
            }
        } else {
            // 兼容旧签名：没有 uid 绑定时仍然要求当前会话在线，避免旧链接退化成匿名可读。
            if (currentLoginUserId == null) {
                writeUnauthorized((HttpServletResponse) response);
                return;
            }
            if (UploadAccessSignatureService.MODE_PROJECT.equals(access.mode())
                    && (access.projectId() == null || !projectAccessGuard.canAccess(access.projectId()))) {
                writeForbidden((HttpServletResponse) response);
                return;
            }
        }
        req.setAttribute(SecurityRequestAttributes.UPLOAD_ACCESS_MODE, access.mode());
        req.setAttribute(SecurityRequestAttributes.UPLOAD_PROJECT_ID, access.projectId());
        req.setAttribute(SecurityRequestAttributes.UPLOAD_SIGNED_USER_ID, access.userId());

        chain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"请登录后访问该资源\",\"data\":null}");
        response.getWriter().flush();
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"资源访问链接已失效，请刷新后重试\",\"data\":null}");
        response.getWriter().flush();
    }
}
