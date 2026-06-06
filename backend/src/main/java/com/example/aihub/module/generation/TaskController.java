package com.example.aihub.module.generation;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.dto.TaskQueryDTO;
import com.example.aihub.infrastructure.service.GenerationService;
import com.example.aihub.infrastructure.vo.GenerationTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tasks")
@SaCheckLogin
public class TaskController {
    private final GenerationService generationService;

    @GetMapping
    public ApiResult<List<GenerationTaskVO>> list(TaskQueryDTO query) {
        query.setLimit(PagingUtil.clampLimit(query.getLimit() == null ? 0 : query.getLimit(), 100, 100));
        return ApiResult.ok(generationService.list(query));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<GenerationTaskVO>> page(TaskQueryDTO query) {
        query.setPage(PagingUtil.normalizePage(query.getPage()));
        query.setPageSize(PagingUtil.clampPageSize(query.getPageSize(), 100));
        return ApiResult.ok(generationService.page(query));
    }

    @GetMapping("/{id}")
    public ApiResult<GenerationTaskVO> detail(@PathVariable Long id) {
        return ApiResult.ok(generationService.getTask(id));
    }

    @PostMapping("/{id}/retry")
    public ApiResult<GenerationTaskVO> retry(@PathVariable Long id) {
        return ApiResult.ok(generationService.retry(id));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        generationService.delete(id);
        return ApiResult.ok();
    }
}
