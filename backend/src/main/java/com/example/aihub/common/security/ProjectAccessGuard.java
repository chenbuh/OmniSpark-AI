package com.example.aihub.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.SecurityUtil;
import com.example.aihub.infrastructure.entity.Project;
import com.example.aihub.infrastructure.entity.ProjectShare;
import com.example.aihub.infrastructure.entity.TeamMember;
import com.example.aihub.infrastructure.mapper.ProjectMapper;
import com.example.aihub.infrastructure.mapper.ProjectShareMapper;
import com.example.aihub.infrastructure.mapper.TeamMemberMapper;
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

    /** 当前用户是否可访问该项目；不可访问返回 false。 */
    public boolean canAccess(Long projectId) {
        if (projectId == null || projectId == 0L) {
            return false;
        }
        Long userId = SecurityUtil.loginUserId();
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return false;
        }
        if (project.getUserId().equals(userId)) {
            return true;
        }
        return hasTeamShare(projectId, userId);
    }

    /** 校验当前用户可访问该项目，否则抛出业务异常。 */
    public void assertAccess(Long projectId) {
        if (!canAccess(projectId)) {
            throw new BusinessException("无权访问该资源");
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

    private boolean hasTeamShare(Long projectId, Long userId) {
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, 1));
        if (memberships.isEmpty()) {
            return false;
        }
        Set<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).collect(Collectors.toSet());
        Long count = shareMapper.selectCount(
                new LambdaQueryWrapper<ProjectShare>()
                        .eq(ProjectShare::getProjectId, projectId)
                        .in(ProjectShare::getTeamId, teamIds));
        return count != null && count > 0;
    }
}
