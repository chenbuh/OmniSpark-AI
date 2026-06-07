package com.example.aihub.module.team;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.dto.TeamMemberInviteDTO;
import com.example.aihub.infrastructure.dto.TeamSaveDTO;
import com.example.aihub.infrastructure.service.TeamService;
import com.example.aihub.infrastructure.vo.TeamMemberVO;
import com.example.aihub.infrastructure.vo.TeamVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teams")
@SaCheckLogin
public class TeamController {
    private final TeamService teamService;

    @GetMapping
    public ApiResult<List<TeamVO>> list(@RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(teamService.listMyTeams(PagingUtil.clampLimit(limit, 100, 100)));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<TeamVO>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResult.ok(teamService.pageMyTeams(page, PagingUtil.clampPageSize(pageSize, 100)));
    }

    @GetMapping("/{id}")
    public ApiResult<TeamVO> get(@PathVariable Long id) {
        return ApiResult.ok(teamService.getTeam(id));
    }

    @PostMapping
    public ApiResult<TeamVO> create(@Valid @RequestBody TeamSaveDTO dto) {
        return ApiResult.ok(teamService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResult<TeamVO> update(@PathVariable Long id, @Valid @RequestBody TeamSaveDTO dto) {
        return ApiResult.ok(teamService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        teamService.delete(id);
        return ApiResult.ok();
    }

    // ===== 成员管理 =====

    @GetMapping("/{id}/members")
    public ApiResult<List<TeamMemberVO>> listMembers(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(teamService.listMembers(id, PagingUtil.clampLimit(limit, 100, 100)));
    }

    @GetMapping("/{id}/members/page")
    public ApiResult<PageResult<TeamMemberVO>> pageMembers(@PathVariable Long id,
                                                           @RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResult.ok(teamService.pageMembers(id, page, PagingUtil.clampPageSize(pageSize, 100)));
    }

    @GetMapping("/{teamId}/members/{userId}")
    public ApiResult<TeamMemberVO> getMember(@PathVariable Long teamId, @PathVariable Long userId) {
        return ApiResult.ok(teamService.getMember(teamId, userId));
    }

    @PostMapping("/members/invite")
    public ApiResult<TeamMemberVO> invite(@Valid @RequestBody TeamMemberInviteDTO dto) {
        return ApiResult.ok(teamService.inviteMember(dto));
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ApiResult<Void> removeMember(@PathVariable Long teamId, @PathVariable Long userId) {
        teamService.removeMember(teamId, userId);
        return ApiResult.ok();
    }

    @PostMapping("/{teamId}/leave")
    public ApiResult<Void> leave(@PathVariable Long teamId) {
        teamService.leaveTeam(teamId);
        return ApiResult.ok();
    }
}
