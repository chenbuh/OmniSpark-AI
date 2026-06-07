package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.util.SecurityUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.TeamMemberInviteDTO;
import com.example.aihub.infrastructure.dto.TeamSaveDTO;
import com.example.aihub.infrastructure.entity.Team;
import com.example.aihub.infrastructure.entity.TeamMember;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.TeamMapper;
import com.example.aihub.infrastructure.mapper.TeamMemberMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import com.example.aihub.infrastructure.vo.TeamMemberVO;
import com.example.aihub.infrastructure.vo.TeamVO;
import com.example.aihub.infrastructure.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    // ===== 团队 CRUD =====

    public PageResult<TeamVO> pageMyTeams(long page, long pageSize) {
        Long userId = SecurityUtil.loginUserId();
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 20);
        Page<TeamMember> memberPage = memberMapper.selectPage(
                new Page<>(safePage, safePageSize),
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, 1)
                        .orderByDesc(TeamMember::getId)
        );
        List<Long> teamIds = memberPage.getRecords().stream().map(TeamMember::getTeamId).toList();
        if (teamIds.isEmpty()) {
            return new PageResult<>(memberPage.getTotal(), memberPage.getPages(), List.of());
        }
        Map<Long, Team> teamMap = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                        .in(Team::getId, teamIds)
                        .eq(Team::getStatus, 1))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Team::getId, Function.identity()));
        Map<Long, Integer> orderMap = new java.util.HashMap<>();
        for (int i = 0; i < teamIds.size(); i++) {
            orderMap.put(teamIds.get(i), i);
        }
        // 批量加载负责人信息
        Map<Long, User> ownerMap = batchLoadUsersByIds(
                teamMap.values().stream().map(Team::getOwnerId).collect(Collectors.toSet()));
        // 批量加载成员数
        Map<Long, Long> memberCountMap = batchCountMembers(teamIds);
        List<TeamVO> records = teamMap.values().stream()
                .sorted(Comparator.comparingInt(team -> orderMap.getOrDefault(team.getId(), Integer.MAX_VALUE)))
                .map(team -> toTeamVO(team, ownerMap.get(team.getOwnerId()), memberCountMap.getOrDefault(team.getId(), 0L)))
                .toList();
        return new PageResult<>(memberPage.getTotal(), memberPage.getPages(), records);
    }

    public List<TeamVO> listMyTeams(int limit) {
        Long userId = SecurityUtil.loginUserId();
        List<TeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, 1)
                        .orderByDesc(TeamMember::getId)
                        .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)));
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> teamIds = members.stream().map(TeamMember::getTeamId).toList();
        List<Team> teams = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                        .in(Team::getId, teamIds)
                        .eq(Team::getStatus, 1)
                        .orderByDesc(Team::getId)
                        .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)));
        if (teams.isEmpty()) {
            return List.of();
        }
        // 批量加载负责人信息和成员数
        Map<Long, User> ownerMap = batchLoadUsersByIds(
                teams.stream().map(Team::getOwnerId).collect(Collectors.toSet()));
        Map<Long, Long> memberCountMap = batchCountMembers(
                teams.stream().map(Team::getId).toList());
        return teams.stream()
                .map(team -> toTeamVO(team, ownerMap.get(team.getOwnerId()), memberCountMap.getOrDefault(team.getId(), 0L)))
                .toList();
    }

    public TeamVO getTeam(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException("团队不存在");
        }
        requireTeamMember(team);
        return toTeamVO(team);
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamVO create(TeamSaveDTO dto) {
        Long userId = SecurityUtil.loginUserId();
        Team team = new Team();
        team.setName(dto.getName());
        team.setDescription(dto.getDescription());
        team.setOwnerId(userId);
        team.setStatus(1);
        teamMapper.insert(team);

        TeamMember owner = new TeamMember();
        owner.setTeamId(team.getId());
        owner.setUserId(userId);
        owner.setRole("owner");
        owner.setStatus(1);
        memberMapper.insert(owner);

        return toTeamVO(team);
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamVO update(Long teamId, TeamSaveDTO dto) {
        Team team = requireTeam(teamId);
        requireTeamOwner(team);
        team.setName(dto.getName());
        team.setDescription(dto.getDescription());
        teamMapper.updateById(team);
        return toTeamVO(team);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long teamId) {
        Team team = requireTeam(teamId);
        requireTeamOwner(team);
        team.setStatus(0);
        teamMapper.updateById(team);
        memberMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .set(TeamMember::getStatus, 0));
    }

    // ===== 成员管理 =====

    public PageResult<TeamMemberVO> pageMembers(Long teamId, long page, long pageSize) {
        Team team = requireTeam(teamId);
        requireTeamMember(team);
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 20);
        Page<TeamMember> result = memberMapper.selectPage(
                new Page<>(safePage, safePageSize),
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getStatus, 1)
                        .orderByDesc(TeamMember::getId)
        );
        List<TeamMember> records = result.getRecords();
        Map<Long, User> userMap = batchLoadUsersByIds(
                records.stream().map(TeamMember::getUserId).collect(Collectors.toSet()));
        return new PageResult<>(result.getTotal(), result.getPages(),
                records.stream().map(m -> toTeamMemberVO(m, userMap.get(m.getUserId()))).toList());
    }

    public List<TeamMemberVO> listMembers(Long teamId, int limit) {
        Team team = requireTeam(teamId);
        requireTeamMember(team);
        List<TeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getStatus, 1)
                        .orderByDesc(TeamMember::getId)
                        .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)));
        Map<Long, User> userMap = batchLoadUsersByIds(
                members.stream().map(TeamMember::getUserId).collect(Collectors.toSet()));
        return members.stream().map(m -> toTeamMemberVO(m, userMap.get(m.getUserId()))).toList();
    }

    public TeamMemberVO getMember(Long teamId, Long userId) {
        Team team = requireTeam(teamId);
        requireTeamMember(team);
        TeamMember member = memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getStatus, 1));
        if (member == null) {
            throw new BusinessException("团队成员不存在");
        }
        return toTeamMemberVO(member);
    }

    @Transactional(rollbackFor = Exception.class)
    public TeamMemberVO inviteMember(TeamMemberInviteDTO dto) {
        Team team = requireTeam(dto.getTeamId());
        requireTeamAdmin(team);

        User invitedUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (invitedUser == null) {
            throw new BusinessException("用户不存在");
        }

        Long existing = memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, dto.getTeamId())
                .eq(TeamMember::getUserId, invitedUser.getId())
                .eq(TeamMember::getStatus, 1));
        if (existing != null && existing > 0) {
            throw new BusinessException("该用户已是团队成员");
        }

        TeamMember member = new TeamMember();
        member.setTeamId(dto.getTeamId());
        member.setUserId(invitedUser.getId());
        member.setRole(dto.getRole() != null ? dto.getRole() : "member");
        member.setStatus(1);
        memberMapper.insert(member);

        // 发送通知
        try {
            notificationService.notify(
                    invitedUser.getId(),
                    "团队邀请",
                    "您已被邀请加入团队「" + team.getName() + "」",
                    "team_invite",
                    team.getId()
            );
        } catch (Exception ignored) {}

        TeamMemberVO vo = VoMapper.copy(member, TeamMemberVO.class);
        vo.setUsername(invitedUser.getUsername());
        vo.setNickname(invitedUser.getNickname());
        vo.setAvatar(invitedUser.getAvatar());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long teamId, Long userId) {
        Team team = requireTeam(teamId);
        requireTeamAdmin(team);

        if (team.getOwnerId().equals(userId)) {
            throw new BusinessException("不能移除团队所有者");
        }

        TeamMember member = memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getStatus, 1));
        if (member == null) {
            throw new BusinessException("团队成员不存在");
        }

        memberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void leaveTeam(Long teamId) {
        Long userId = SecurityUtil.loginUserId();
        Team team = requireTeam(teamId);
        if (team.getOwnerId().equals(userId)) {
            throw new BusinessException("团队所有者不能退出，请先转让所有权");
        }

        TeamMember member = memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getStatus, 1));
        if (member == null) {
            throw new BusinessException("您不在该团队中");
        }

        memberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
    }

    // ===== 权限校验 =====

    public boolean isTeamMember(Long teamId, Long userId) {
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getStatus, 1));
        return count != null && count > 0;
    }

    public boolean isTeamAdmin(Long teamId, Long userId) {
        TeamMember member = memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getStatus, 1));
        if (member == null) return false;
        return "owner".equals(member.getRole()) || "admin".equals(member.getRole());
    }

    // ===== 内部方法 =====

    private Team requireTeam(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null || (team.getStatus() != null && team.getStatus() == 0)) {
            throw new BusinessException("团队不存在或已解散");
        }
        return team;
    }

    private void requireTeamOwner(Team team) {
        if (!team.getOwnerId().equals(SecurityUtil.loginUserId())) {
            throw new BusinessException("只有团队所有者才能执行此操作");
        }
    }

    private void requireTeamAdmin(Team team) {
        Long userId = SecurityUtil.loginUserId();
        if (team.getOwnerId().equals(userId)) return;
        if (!isTeamAdmin(team.getId(), userId)) {
            throw new BusinessException("没有足够的权限");
        }
    }

    private void requireTeamMember(Team team) {
        Long userId = SecurityUtil.loginUserId();
        if (team.getOwnerId().equals(userId)) {
            return;
        }
        if (!isTeamMember(team.getId(), userId)) {
            throw new BusinessException("您不在该团队中");
        }
    }

    private TeamVO toTeamVO(Team team) {
        return toTeamVO(team, null, 0L);
    }

    private TeamVO toTeamVO(Team team, User owner, long memberCount) {
        TeamVO vo = VoMapper.copy(team, TeamVO.class);
        if (owner != null) {
            vo.setOwnerName(owner.getNickname() != null ? owner.getNickname() : owner.getUsername());
        } else if (team.getOwnerId() != null) {
            User fetched = userMapper.selectById(team.getOwnerId());
            if (fetched != null) {
                vo.setOwnerName(fetched.getNickname() != null ? fetched.getNickname() : fetched.getUsername());
            }
        }
        if (memberCount > 0) {
            vo.setMemberCount((int) memberCount);
        } else {
            Long count = memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getTeamId, team.getId())
                    .eq(TeamMember::getStatus, 1));
            vo.setMemberCount(count != null ? count.intValue() : 0);
        }
        return vo;
    }

    private TeamMemberVO toTeamMemberVO(TeamMember member) {
        User user = userMapper.selectById(member.getUserId());
        return toTeamMemberVO(member, user);
    }

    private TeamMemberVO toTeamMemberVO(TeamMember member, User user) {
        TeamMemberVO vo = VoMapper.copy(member, TeamMemberVO.class);
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }
        return vo;
    }

    // ===== 批量查询辅助 =====

    /** 批量查询用户，返回 userId → User 的 Map。 */
    private Map<Long, User> batchLoadUsersByIds(java.util.Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getId, userIds))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 批量统计团队活跃成员数，返回 teamId → count 的 Map。 */
    private Map<Long, Long> batchCountMembers(List<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> counts = memberMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TeamMember>()
                        .select("team_id", "COUNT(*) AS cnt")
                        .in("team_id", teamIds)
                        .eq("status", 1)
                        .groupBy("team_id"));
        Map<Long, Long> result = new java.util.HashMap<>();
        for (Map<String, Object> row : counts) {
            Number teamId = (Number) row.get("team_id");
            Number cnt = (Number) row.get("cnt");
            if (teamId != null) {
                result.put(teamId.longValue(), cnt != null ? cnt.longValue() : 0L);
            }
        }
        return result;
    }
}
