package com.example.aihub.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.SecurityUtil;
import com.example.aihub.infrastructure.entity.Project;
import com.example.aihub.infrastructure.entity.ProjectShare;
import com.example.aihub.infrastructure.entity.TeamMember;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.ProjectMapper;
import com.example.aihub.infrastructure.mapper.ProjectShareMapper;
import com.example.aihub.infrastructure.mapper.TeamMemberMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目访问归属校验器。
 * 用于在 Service 层校验「当前登录用户是否有权访问某个项目（及其下的资源）」，
 * 弥补 {@code @RequireProjectPermission} 切面无法覆盖「只有资源 id、没有 projectId 参数」的场景。
 */
@Component
@RequiredArgsConstructor
public class ProjectAccessGuard {
    private final ProjectMapper projectMapper;
    private final ProjectShareMapper shareMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;

    /** 当前用户是否可访问该项目；不可访问返回 false。 */
    public boolean canAccess(Long projectId) {
        return canUserAccess(SecurityUtil.loginUserId(), projectId);
    }

    /** 指定用户是否可访问该项目；不可访问返回 false。 */
    public boolean canUserAccess(Long userId, Long projectId) {
        return canUserAccess(userId, projectId, "view");
    }

    /** 指定用户是否拥有项目所需权限；permission 取值 view / edit / admin。 */
    public boolean canUserAccess(Long userId, Long projectId, String requiredPermission) {
        if (userId == null || projectId == null || projectId == 0L) {
            return false;
        }
        if (isAdmin(userId)) {
            return true;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return false;
        }
        if (project.getUserId().equals(userId)) {
            return true;
        }
        return hasTeamShare(projectId, userId, requiredPermission);
    }

    /** 校验当前用户可访问该项目，否则抛出业务异常。 */
    public void assertAccess(Long projectId) {
        if (!canAccess(projectId)) {
            throw new BusinessException("无权访问该资源");
        }
    }

    /** 校验当前用户拥有项目编辑权限，否则抛出业务异常。 */
    public void assertEditAccess(Long projectId) {
        assertPermission(projectId, "edit");
    }

    /** 校验当前用户拥有项目管理权限，否则抛出业务异常。 */
    public void assertAdminAccess(Long projectId) {
        assertPermission(projectId, "admin");
    }

    /** 校验当前用户拥有项目所需权限，否则抛出业务异常。 */
    public void assertPermission(Long projectId, String requiredPermission) {
        if (!canUserAccess(SecurityUtil.loginUserId(), projectId, requiredPermission)) {
            throw new BusinessException("无权执行该操作");
        }
    }

    /**
     * 当前登录用户可访问的项目 id 集合：本人拥有的项目 + 所在团队被共享的项目。
     * 用于 list 类接口在未显式指定 projectId 时按归属过滤，避免返回全量数据或全局公共数据。
     */
    public List<Long> accessibleProjectIds() {
        Long userId = SecurityUtil.loginUserId();
        Set<Long> ids = new HashSet<>();

        List<Project> owned = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getUserId, userId));
        owned.forEach(p -> ids.add(p.getId()));

        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, 1));
        if (!memberships.isEmpty()) {
            Set<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).collect(Collectors.toSet());
            List<ProjectShare> shares = shareMapper.selectList(
                    new LambdaQueryWrapper<ProjectShare>().in(ProjectShare::getTeamId, teamIds));
            shares.forEach(s -> ids.add(s.getProjectId()));
        }
        return new ArrayList<>(ids);
    }

    private boolean hasTeamShare(Long projectId, Long userId, String requiredPermission) {
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, 1));
        if (memberships.isEmpty()) {
            return false;
        }
        Set<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).collect(Collectors.toSet());
        List<ProjectShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<ProjectShare>()
                        .eq(ProjectShare::getProjectId, projectId)
                        .in(ProjectShare::getTeamId, teamIds));
        if (shares.isEmpty()) {
            return false;
        }
        int requiredLevel = permissionLevel(requiredPermission);
        for (ProjectShare share : shares) {
            if (permissionLevel(share.getPermission()) >= requiredLevel) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdmin(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && "admin".equalsIgnoreCase(user.getRole());
    }

    private int permissionLevel(String permission) {
        if (permission == null) {
            return 0;
        }
        return switch (permission.trim().toLowerCase()) {
            case "admin" -> 3;
            case "edit" -> 2;
            case "view" -> 1;
            default -> 0;
        };
    }
}
