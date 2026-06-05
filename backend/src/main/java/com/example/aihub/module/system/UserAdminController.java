package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aihub.common.annotation.RateLimit;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.security.CanaryTokenService;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.util.PasswordUtil;
import com.example.aihub.common.util.SecurityUtil;
import com.example.aihub.infrastructure.entity.Role;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.RoleMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import com.example.aihub.infrastructure.service.PasswordEncryptionService;
import com.example.aihub.infrastructure.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@SaCheckLogin
@SaCheckRole("admin")
public class UserAdminController {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncryptionService passwordEncryptionService;
    private final CanaryTokenService canaryTokenService;

    @GetMapping("/roles")
    public ApiResult<List<Map<String, String>>> roles() {
        List<Map<String, String>> records = loadActiveRoles().stream()
                .map(role -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("value", role.getRoleCode());
                    item.put("label", role.getRoleName());
                    return item;
                })
                .toList();
        return ApiResult.ok(records);
    }

    @GetMapping
    public ApiResult<PageResult<UserVO>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 10);
        var wrapper = new LambdaQueryWrapper<User>();
        if (search != null && !search.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, search).or().like(User::getNickname, search));
        }
        wrapper.orderByDesc(User::getId);

        var p = userMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        List<UserVO> records = p.getRecords().stream().map(u -> {
            UserVO vo = new UserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
            vo.setRole(u.getRole());
            vo.setStatus(u.getStatus());
            vo.setCreatedAt(u.getCreatedAt());
            return vo;
        }).toList();
        return ApiResult.ok(new PageResult<>(p.getTotal(), p.getPages(), records));
    }

    @PutMapping("/{id}/role")
    public ApiResult<Void> updateRole(@PathVariable Long id, @RequestParam String role) {
        if (!isAllowedRole(role)) {
            return ApiResult.fail("非法的角色，角色必须存在于角色表且处于启用状态");
        }
        if (id.equals(SecurityUtil.loginUserId())) {
            return ApiResult.fail("不能修改自己的角色");
        }
        User user = userMapper.selectById(id);
        if (user == null) return ApiResult.fail("用户不存在");
        user.setRole(role);
        userMapper.updateById(user);
        return ApiResult.ok();
    }

    @PutMapping("/{id}/status")
    public ApiResult<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        if (id.equals(SecurityUtil.loginUserId())) {
            return ApiResult.fail("不能修改自己的状态");
        }
        User user = userMapper.selectById(id);
        if (user == null) return ApiResult.fail("用户不存在");
        user.setStatus(status);
        userMapper.updateById(user);
        return ApiResult.ok();
    }

    // ===== 创建用户 =====

    @PostMapping
    public ApiResult<UserVO> create(@RequestParam String username,
                                     @RequestParam(required = false) String password,
                                     @RequestParam(required = false) String encryptedPassword,
                                     @RequestParam(required = false, defaultValue = "") String nickname,
                                     @RequestParam(required = false) String role) {
        String resolvedRole = defaultIfBlank(role, resolveDefaultRoleCode());
        if (!isAllowedRole(resolvedRole)) {
            return ApiResult.fail("非法的角色，角色必须存在于角色表且处于启用状态");
        }
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            return ApiResult.fail("用户名已存在");
        }
        // 未指定密码时随机生成，并在响应中返回供管理员转交，避免硬编码弱口令
        boolean generated = (password == null || password.isBlank())
                && (encryptedPassword == null || encryptedPassword.isBlank());
        String rawPassword = generated
                ? generateRandomPassword()
                : passwordEncryptionService.resolvePassword(password, encryptedPassword);
        if (!generated) {
            String passwordError = PasswordUtil.getPasswordValidationError(rawPassword, username);
            if (passwordError != null) {
                return ApiResult.fail(passwordError);
            }
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(rawPassword));
        user.setNickname(nickname.isBlank() ? username : nickname);
        user.setRole(resolvedRole);
        user.setStatus(1);
        userMapper.insert(user);

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        if (generated) {
            vo.setInitialPassword(rawPassword);
        }
        return ApiResult.ok(vo);
    }

    // ===== 更新昵称 =====

    @PutMapping("/{id}/nickname")
    public ApiResult<Void> updateNickname(@PathVariable Long id, @RequestParam String nickname) {
        User user = userMapper.selectById(id);
        if (user == null) return ApiResult.fail("用户不存在");
        user.setNickname(nickname);
        userMapper.updateById(user);
        return ApiResult.ok();
    }

    // ===== 重置密码 =====

    @PutMapping("/{id}/reset-password")
    public ApiResult<Map<String, Object>> resetPassword(@PathVariable Long id,
                                          @RequestParam(required = false) String password,
                                          @RequestParam(required = false) String encryptedPassword) {
        User user = userMapper.selectById(id);
        if (user == null) return ApiResult.fail("用户不存在");
        // 未指定密码时随机生成，并返回供管理员转交
        boolean generated = (password == null || password.isBlank())
                && (encryptedPassword == null || encryptedPassword.isBlank());
        String rawPassword = generated
                ? generateRandomPassword()
                : passwordEncryptionService.resolvePassword(password, encryptedPassword);
        if (!generated) {
            String passwordError = PasswordUtil.getPasswordValidationError(rawPassword, user.getUsername());
            if (passwordError != null) {
                return ApiResult.fail(passwordError);
            }
        }
        user.setPassword(PasswordUtil.encode(rawPassword));
        userMapper.updateById(user);
        Map<String, Object> result = new LinkedHashMap<>();
        if (generated) {
            result.put("initialPassword", rawPassword);
        }
        return ApiResult.ok(result);
    }

    // ===== 删除 =====

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        if (id.equals(SecurityUtil.loginUserId())) {
            return ApiResult.fail("不能删除自己的账号");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            return ApiResult.fail("用户不存在");
        }
        userMapper.deleteById(id);
        return ApiResult.ok();
    }

    /** 生成 12 位随机初始密码(含大小写字母与数字),替代硬编码弱口令。 */
    private String generateRandomPassword() {
        final String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ===== 导出 =====

    @GetMapping("/export")
    @RateLimit(count = 10, seconds = 3600, dimension = RateLimit.Dimension.IP, message = "用户导出过于频繁，请稍后再试")
    @RateLimit(count = 5, seconds = 3600, dimension = RateLimit.Dimension.USER_API, message = "用户导出过于频繁，请稍后再试")
    public void exportCsv(HttpServletResponse response, HttpServletRequest request) throws Exception {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=users_" + java.time.LocalDate.now() + ".csv");

        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF"); // BOM for Excel UTF-8
        csv.append("# canary,").append(com.example.aihub.common.util.CsvUtil.escape(
                canaryTokenService.create("admin_users_csv", "users", request))).append("\n");
        csv.append("ID,用户名,昵称,角色,状态\n");

        List<User> users = userMapper.selectList(null);
        for (User u : users) {
            csv.append(com.example.aihub.common.util.CsvUtil.escape(u.getId())).append(",")
               .append(com.example.aihub.common.util.CsvUtil.escape(u.getUsername())).append(",")
               .append(com.example.aihub.common.util.CsvUtil.escape(u.getNickname() != null ? u.getNickname() : "")).append(",")
               .append(com.example.aihub.common.util.CsvUtil.escape(u.getRole())).append(",")
               .append(com.example.aihub.common.util.CsvUtil.escape(u.getStatus())).append("\n");
        }
        response.getWriter().write(csv.toString());
    }

    // ===== 导入 =====

    @PostMapping("/import")
    public ApiResult<Map<String, Object>> importCsv(@RequestBody String csvBody) {
        Map<String, Object> result = new LinkedHashMap<>();
        int success = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        List<Map<String, String>> generatedCredentials = new ArrayList<>();

        try {
            String[] lines = csvBody.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isBlank()) continue;
                String normalizedLine = line.startsWith("\uFEFF") ? line.substring(1) : line;
                if (normalizedLine.startsWith("#") || normalizedLine.startsWith("ID,") || normalizedLine.startsWith("用户名,")) {
                    continue;
                }
                String[] parts = normalizedLine.split(",", -1);
                if (parts.length < 2) { failed++; continue; }

                try {
                    boolean exportedRow = isNumeric(parts[0].trim());
                    String username = exportedRow ? safePart(parts, 1) : safePart(parts, 0);
                    if (username.isBlank()) {
                        errors.add("行 " + (i + 1) + " 用户名为空，已跳过");
                        failed++;
                        continue;
                    }
                    String importedPassword = exportedRow ? "" : safePart(parts, 1);
                    boolean generatedPassword = importedPassword.isBlank();
                    String password = generatedPassword ? generateRandomPassword() : importedPassword;
                    if (!generatedPassword) {
                        String passwordError = PasswordUtil.getPasswordValidationError(password, username);
                        if (passwordError != null) {
                            errors.add("用户 " + username + " 密码不符合要求: " + passwordError);
                            failed++;
                            continue;
                        }
                    }
                    String nickname = exportedRow
                            ? defaultIfBlank(safePart(parts, 2), username)
                            : defaultIfBlank(safePart(parts, 2), username);
                    String role = exportedRow
                            ? defaultIfBlank(safePart(parts, 3), resolveDefaultRoleCode())
                            : defaultIfBlank(safePart(parts, 3), resolveDefaultRoleCode());
                    int status = exportedRow ? parseStatus(safePart(parts, 4)) : 1;
                    if (!isAllowedRole(role)) {
                        errors.add("用户 " + username + " 角色非法，角色必须存在于角色表且处于启用状态");
                        failed++;
                        continue;
                    }

                    // 检查是否已存在
                    Long exists = userMapper.selectCount(
                            new LambdaQueryWrapper<User>()
                                    .eq(User::getUsername, username));
                    if (exists != null && exists > 0) {
                        errors.add("用户 " + username + " 已存在，跳过");
                        failed++; continue;
                    }

                    User user = new User();
                    user.setUsername(username);
                    user.setPassword(PasswordUtil.encode(password));
                    user.setNickname(nickname);
                    user.setRole(role);
                    user.setStatus(status);
                    userMapper.insert(user);
                    if (generatedPassword) {
                        Map<String, String> credential = new LinkedHashMap<>();
                        credential.put("username", username);
                        credential.put("initialPassword", password);
                        generatedCredentials.add(credential);
                    }
                    success++;
                } catch (Exception e) {
                    errors.add("行 " + (i + 1) + " 导入失败: " + e.getMessage());
                    failed++;
                }
            }
        } catch (Exception e) {
            return ApiResult.fail("导入失败: " + e.getMessage());
        }

        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors);
        result.put("generatedCredentials", generatedCredentials);
        return ApiResult.ok(result);
    }

    private static String safePart(String[] parts, int index) {
        return parts.length > index ? parts[index].trim() : "";
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int parseStatus(String value) {
        return "0".equals(value) ? 0 : 1;
    }

    private List<Role> loadActiveRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>()
                        .eq(Role::getStatus, 1))
                .stream()
                .filter(role -> role.getRoleCode() != null && !role.getRoleCode().isBlank())
                .sorted(Comparator.comparingInt(this::roleSortOrder)
                        .thenComparing(Role::getRoleName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private boolean isAllowedRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return loadActiveRoles().stream().anyMatch(role -> roleCode.equals(role.getRoleCode()));
    }

    private String resolveDefaultRoleCode() {
        List<Role> roles = loadActiveRoles();
        return roles.stream()
                .map(Role::getRoleCode)
                .filter("user"::equals)
                .findFirst()
                .or(() -> roles.stream().map(Role::getRoleCode).findFirst())
                .orElse("user");
    }

    private int roleSortOrder(Role role) {
        return switch (role.getRoleCode()) {
            case "admin" -> 10;
            case "user" -> 20;
            default -> 100;
        };
    }
}
