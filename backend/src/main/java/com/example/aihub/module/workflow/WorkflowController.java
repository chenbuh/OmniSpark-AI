package com.example.aihub.module.workflow;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.dto.WorkflowSaveDTO;
import com.example.aihub.infrastructure.service.WorkflowService;
import com.example.aihub.infrastructure.vo.WorkflowMetaVO;
import com.example.aihub.infrastructure.vo.WorkflowRunVO;
import com.example.aihub.infrastructure.vo.WorkflowVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflows")
@SaCheckLogin
public class WorkflowController {
    private final WorkflowService workflowService;

    @GetMapping("/meta")
    public ApiResult<WorkflowMetaVO> meta() {
        return ApiResult.ok(workflowService.meta());
    }

    @GetMapping
    public ApiResult<List<WorkflowVO>> list(@RequestParam(name = "projectId", required = false) Long projectId,
                                            @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(workflowService.list(projectId, PagingUtil.clampLimit(limit, 100, 100)));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<WorkflowVO>> page(@RequestParam(name = "projectId", required = false) Long projectId,
                                                  @RequestParam(name = "q", required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResult.ok(workflowService.page(projectId, keyword, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResult<WorkflowVO> get(@PathVariable Long id) {
        return ApiResult.ok(workflowService.get(id));
    }

    @PostMapping
    public ApiResult<WorkflowVO> create(@Valid @RequestBody WorkflowSaveDTO dto) {
        return ApiResult.ok(workflowService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResult<WorkflowVO> update(@PathVariable Long id, @Valid @RequestBody WorkflowSaveDTO dto) {
        return ApiResult.ok(workflowService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return ApiResult.ok();
    }

    // ===== 执行与运行历史 =====

    @PostMapping("/{id}/execute")
    public ApiResult<WorkflowRunVO> execute(@PathVariable Long id) {
        return ApiResult.ok(workflowService.execute(id));
    }

    @GetMapping("/{id}/runs")
    public ApiResult<List<WorkflowRunVO>> runs(@PathVariable Long id,
                                               @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(workflowService.listRuns(id, PagingUtil.clampLimit(limit, 100, 100)));
    }

    @GetMapping("/{id}/runs/page")
    public ApiResult<PageResult<WorkflowRunVO>> pageRuns(@PathVariable Long id,
                                                         @RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(defaultValue = "100") long pageSize) {
        return ApiResult.ok(workflowService.pageRuns(id, page, pageSize));
    }

    @GetMapping("/runs/{runId}")
    public ApiResult<WorkflowRunVO> getRun(@PathVariable Long runId) {
        return ApiResult.ok(workflowService.getRun(runId));
    }
}
