package com.example.aihub.module.quota;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.service.QuotaService;
import com.example.aihub.infrastructure.vo.QuotaRecordVO;
import com.example.aihub.infrastructure.vo.QuotaSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/quota")
@SaCheckLogin
public class QuotaController {
    private final QuotaService quotaService;

    @GetMapping("/summary")
    public ApiResult<QuotaSummaryVO> summary() {
        return ApiResult.ok(quotaService.summary());
    }

    @GetMapping("/records")
    public ApiResult<List<QuotaRecordVO>> records(@RequestParam(name = "projectId", required = false) Long projectId,
                                                  @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(quotaService.records(projectId, PagingUtil.clampLimit(limit, 100, 100)));
    }
}
