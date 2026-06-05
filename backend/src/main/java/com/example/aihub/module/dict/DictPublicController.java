package com.example.aihub.module.dict;

import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.entity.DataDictItem;
import com.example.aihub.infrastructure.service.DataDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dict")
public class DictPublicController {
    private final DataDictService dataDictService;

    @GetMapping("/{dictCode}")
    public ApiResult<List<DataDictItem>> getByCode(@PathVariable String dictCode,
                                                   @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(dataDictService.getItemsByCode(dictCode, PagingUtil.clampLimit(limit, 100, 100)));
    }
}
