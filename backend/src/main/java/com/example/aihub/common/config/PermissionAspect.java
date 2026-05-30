package com.example.aihub.common.config;

import com.example.aihub.common.annotation.RequireProjectPermission;
import com.example.aihub.infrastructure.entity.Project;
import com.example.aihub.infrastructure.entity.ProjectShare;
import com.example.aihub.infrastructure.entity.TeamMember;
import com.example.aihub.infrastructure.mapper.ProjectMapper;
import com.example.aihub.infrastructure.mapper.ProjectShareMapper;
import com.example.aihub.infrastructure.mapper.TeamMemberMapper;
import com.example.aihub.infrastructure.service.ProjectShareService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {
    private final ProjectMapper projectMapper;
    private final ProjectShareMapper shareMapper;
    private final TeamMemberMapper teamMemberMapper;

    @Around("@annotation(com.example.aihub.common.annotation.RequireProjectPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireProjectPermission annotation = method.getAnnotation(RequireProjectPermission.class);
        String required = annotation.value();

        Long projectId = extractProjectId(method, joinPoint.getArgs());
        if (projectId == null) {
            // 没有 projectId 参数，放行
            return joinPoint.proceed();
        }

        Long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        Project project = projectMapper.selectById(projectId);

        // 项目不存在或用户是所有者
        if (project == null || project.getUserId().equals(userId)) {
            return joinPoint.proceed();
        }

        // 检查团队共享权限
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, 1));
        if (!memberships.isEmpty()) {
            Set<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).collect(Collectors.toSet());
            List<ProjectShare> shares = shareMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProjectShare>()
                            .eq(ProjectShare::getProjectId, projectId)
                            .in(ProjectShare::getTeamId, teamIds));
            for (ProjectShare share : shares) {
                if (isHigherOrEqual(share.getPermission(), required)) {
                    return joinPoint.proceed();
                }
            }
        }

        throw new com.example.aihub.common.exception.BusinessException("没有足够的权限执行此操作");
    }

    private Long extractProjectId(Method method, Object[] args) {
        // 先从方法参数中找 projectId
        String[] paramNames = new String[args.length];
        // 没有参数名信息，尝试按位置和类型推断
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Long && isProjectIdParam(method, i)) {
                return (Long) args[i];
            }
            // 尝试从 DTO 中反射 projectId 字段
            if (args[i] != null) {
                try {
                    Field f = args[i].getClass().getDeclaredField("projectId");
                    f.setAccessible(true);
                    Object val = f.get(args[i]);
                    if (val instanceof Number) {
                        return ((Number) val).longValue();
                    }
                } catch (NoSuchFieldException ignored) {
                } catch (Exception ignored) {}
            }
        }

        // 按参数名匹配（参数名在编译期可能丢失）
        var params = method.getParameters();
        for (int i = 0; i < params.length && i < args.length; i++) {
            if ("projectId".equals(params[i].getName()) && args[i] instanceof Number) {
                return ((Number) args[i]).longValue();
            }
        }

        return null;
    }

    private boolean isProjectIdParam(Method method, int index) {
        // 启发式：检查参数名（编译期保留时可用）
        try {
            var params = method.getParameters();
            if (index < params.length) {
                return "projectId".equals(params[index].getName());
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isHigherOrEqual(String perm, String required) {
        int p1 = permissionLevel(perm);
        int p2 = permissionLevel(required);
        return p1 >= p2;
    }

    private int permissionLevel(String permission) {
        if (permission == null) return 0;
        return switch (permission.toLowerCase()) {
            case "admin" -> 3;
            case "edit" -> 2;
            case "view" -> 1;
            default -> 0;
        };
    }
}
