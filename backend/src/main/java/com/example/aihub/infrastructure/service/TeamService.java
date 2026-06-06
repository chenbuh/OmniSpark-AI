package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    // ===== 团队 CRUD =====

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
        return teamMapper.selectList(new LambdaQueryWrapper<Team>()
                        .in(Team::getId, teamIds)
                        .eq(Team::getStatus, 1)
                        .orderByDesc(Team::getId)
                        .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)))
                .stream()
                .map(this::toTeamVO)
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

    public List<TeamMemberVO> listMembers(Long teamId, int limit) {
        Team team = requireTeam(teamId);
        requireTeamMember(team);
        List<TeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getStatus, 1)
                        .orderByDesc(TeamMember::getId)
                        .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)));
        return members.stream().map(m -> {
            TeamMemberVO vo = VoMapper.copy(m, TeamMemberVO.class);
            User user = userMapper.selectById(m.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
            }
            return vo;
        }).toList();
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
        TeamVO vo = VoMapper.copy(team, TeamVO.class);
        User owner = userMapper.selectById(team.getOwnerId());
        if (owner != null) {
            vo.setOwnerName(owner.getNickname() != null ? owner.getNickname() : owner.getUsername());
        }
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, team.getId())
                .eq(TeamMember::getStatus, 1));
        vo.setMemberCount(count != null ? count.intValue() : 0);
        return vo;
    }
}
