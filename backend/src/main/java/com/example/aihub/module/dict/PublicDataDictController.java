package com.example.aihub.module.dict;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.entity.DataDictItem;
import com.example.aihub.infrastructure.service.DataDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dict")
@SaCheckLogin
public class PublicDataDictController {
    private final DataDictService dataDictService;

    @GetMapping("/{dictCode}/items")
    public ApiResult<List<DataDictItem>> listEnabledItems(@PathVariable String dictCode,
                                                          @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(dataDictService.getEnabledItemsByCode(dictCode, PagingUtil.clampLimit(limit, 100, 100)));
    }
}
