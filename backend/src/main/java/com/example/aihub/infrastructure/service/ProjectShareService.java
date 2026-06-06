package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.util.SecurityUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.ShareSaveDTO;
import com.example.aihub.infrastructure.entity.Project;
import com.example.aihub.infrastructure.entity.ProjectShare;
import com.example.aihub.infrastructure.entity.Team;
import com.example.aihub.infrastructure.entity.TeamMember;
import com.example.aihub.infrastructure.mapper.ProjectMapper;
import com.example.aihub.infrastructure.mapper.ProjectShareMapper;
import com.example.aihub.infrastructure.mapper.TeamMapper;
import com.example.aihub.infrastructure.mapper.TeamMemberMapper;
import com.example.aihub.infrastructure.vo.ProjectShareVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectShareService {

    private final ProjectShareMapper shareMapper;
    private final ProjectMapper projectMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;

    /**
     * 检查当前登录用户对指定项目是否有指定（或更高级）的权限。
     * 项目所有者拥有全部权限；团队共享按 permission 层级判断。
     *
     * @param projectId       项目 ID
     * @param requiredPermission 需要的权限 (view / edit / admin)
     * @return true 如果有权限
     */
    public boolean checkPermission(Long projectId, String requiredPermission) {
        Long userId = SecurityUtil.loginUserId();
        Project project = projectMapper.selectById(projectId);

        // 项目不存在或已删除
        if (project == null) {
            return false;
        }

        // 项目所有者拥有全部权限
        if (project.getUserId().equals(userId)) {
            return true;
        }

        // 查找该项目通过团队共享给当前用户的最高权限
        List<ProjectShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<ProjectShare>().eq(ProjectShare::getProjectId, projectId));
        if (shares.isEmpty()) {
            return false;
        }

        // 收集用户所属团队
        List<TeamMember> myMemberships = memberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId).eq(TeamMember::getStatus, 1));
        Set<Long> myTeamIds = myMemberships.stream().map(TeamMember::getTeamId).collect(Collectors.toSet());

        // 用户的角色层级
        String myHighestPermission = null;
        for (ProjectShare share : shares) {
            if (!myTeamIds.contains(share.getTeamId())) continue;
            String perm = share.getPermission();
            if (isHigherOrEqual(perm, requiredPermission)) {
                return true;
            }
            if (myHighestPermission == null || isHigherOrEqual(perm, myHighestPermission)) {
                myHighestPermission = perm;
            }
        }

        return myHighestPermission != null && isHigherOrEqual(myHighestPermission, requiredPermission);
    }

    /**
     * 检查权限，无权限时抛出异常
     */
    public void requirePermission(Long projectId, String requiredPermission) {
        if (!checkPermission(projectId, requiredPermission)) {
            throw new BusinessException("没有足够的权限访问该项目");
        }
    }

    /**
     * 获取用户有权访问的所有项目 ID 列表
     */
    public List<Long> getAccessibleProjectIds() {
        Long userId = SecurityUtil.loginUserId();
        // 用户自己的项目
        List<Project> myProjects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getUserId, userId));
        Set<Long> ids = myProjects.stream().map(Project::getId).collect(Collectors.toSet());

        // 通过团队共享的项目
        List<TeamMember> memberships = memberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId).eq(TeamMember::getStatus, 1));
        if (!memberships.isEmpty()) {
            Set<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).collect(Collectors.toSet());
            List<ProjectShare> shares = shareMapper.selectList(
                    new LambdaQueryWrapper<ProjectShare>().in(ProjectShare::getTeamId, teamIds));
            shares.forEach(s -> ids.add(s.getProjectId()));
        }

        return new ArrayList<>(ids);
    }

    // ===== 共享管理 =====

    public PageResult<ProjectShareVO> pageShares(Long projectId, long page, long pageSize) {
        requirePermission(projectId, "admin");

        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 20);
        Page<ProjectShare> result = shareMapper.selectPage(
                new Page<>(safePage, safePageSize),
                new LambdaQueryWrapper<ProjectShare>()
                        .eq(ProjectShare::getProjectId, projectId)
                        .orderByDesc(ProjectShare::getId)
        );
        return new PageResult<>(result.getTotal(), result.getPages(), toShareVOList(result.getRecords()));
    }

    public List<ProjectShareVO> listShares(Long projectId, int limit) {
        // 校验操作者拥有项目 admin 权限
        requirePermission(projectId, "admin");

        List<ProjectShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<ProjectShare>()
                        .eq(ProjectShare::getProjectId, projectId)
                        .orderByDesc(ProjectShare::getId)
                        .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)));
        return toShareVOList(shares);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectShareVO addShare(ShareSaveDTO dto) {
        requirePermission(dto.getProjectId(), "admin");

        // 检查团队存在
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team == null || (team.getStatus() != null && team.getStatus() == 0)) {
            throw new BusinessException("团队不存在");
        }

        // 检查是否已共享
        Long existing = shareMapper.selectCount(new LambdaQueryWrapper<ProjectShare>()
                .eq(ProjectShare::getProjectId, dto.getProjectId())
                .eq(ProjectShare::getTeamId, dto.getTeamId()));
        if (existing != null && existing > 0) {
            throw new BusinessException("该项目已共享给该团队");
        }

        ProjectShare share = new ProjectShare();
        share.setProjectId(dto.getProjectId());
        share.setTeamId(dto.getTeamId());
        share.setPermission(dto.getPermission());
        shareMapper.insert(share);

        ProjectShareVO vo = VoMapper.copy(share, ProjectShareVO.class);
        Project project = projectMapper.selectById(share.getProjectId());
        if (project != null) vo.setProjectName(project.getName());
        Team t = teamMapper.selectById(share.getTeamId());
        if (t != null) vo.setTeamName(t.getName());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeShare(Long shareId) {
        ProjectShare share = shareMapper.selectById(shareId);
        if (share == null) {
            throw new BusinessException("共享记录不存在");
        }
        requirePermission(share.getProjectId(), "admin");
        shareMapper.deleteById(shareId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectShareVO updatePermission(Long shareId, String newPermission) {
        ProjectShare share = shareMapper.selectById(shareId);
        if (share == null) {
            throw new BusinessException("共享记录不存在");
        }
        requirePermission(share.getProjectId(), "admin");
        share.setPermission(newPermission);
        shareMapper.updateById(share);

        ProjectShareVO vo = VoMapper.copy(share, ProjectShareVO.class);
        Project project = projectMapper.selectById(share.getProjectId());
        if (project != null) vo.setProjectName(project.getName());
        Team team = teamMapper.selectById(share.getTeamId());
        if (team != null) vo.setTeamName(team.getName());
        return vo;
    }

    // ===== 内部方法 =====

    private boolean isHigherOrEqual(String perm, String required) {
        // 权限层级: admin > edit > view
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

    private List<ProjectShareVO> toShareVOList(List<ProjectShare> shares) {
        if (shares.isEmpty()) {
            return List.of();
        }
        Set<Long> projectIds = shares.stream().map(ProjectShare::getProjectId).collect(Collectors.toSet());
        Set<Long> teamIds = shares.stream().map(ProjectShare::getTeamId).collect(Collectors.toSet());
        Map<Long, Project> projectMap = projectIds.isEmpty()
                ? Map.of()
                : projectMapper.selectList(new LambdaQueryWrapper<Project>().in(Project::getId, projectIds))
                .stream()
                .collect(Collectors.toMap(Project::getId, project -> project));
        Map<Long, Team> teamMap = teamIds.isEmpty()
                ? Map.of()
                : teamMapper.selectList(new LambdaQueryWrapper<Team>().in(Team::getId, teamIds))
                .stream()
                .collect(Collectors.toMap(Team::getId, team -> team, (left, right) -> left, HashMap::new));
        return shares.stream().map(share -> {
            ProjectShareVO vo = VoMapper.copy(share, ProjectShareVO.class);
            Project project = projectMap.get(share.getProjectId());
            if (project != null) {
                vo.setProjectName(project.getName());
            }
            Team team = teamMap.get(share.getTeamId());
            if (team != null) {
                vo.setTeamName(team.getName());
            }
            return vo;
        }).toList();
    }
}
