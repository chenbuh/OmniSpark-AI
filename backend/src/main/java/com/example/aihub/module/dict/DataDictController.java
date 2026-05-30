package com.example.aihub.module.dict;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.DataDict;
import com.example.aihub.infrastructure.entity.DataDictItem;
import com.example.aihub.infrastructure.service.DataDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/dict")
@SaCheckLogin
@SaCheckRole("admin")
public class DataDictController {
    private final DataDictService dataDictService;

    @GetMapping
    public ApiResult<List<DataDict>> listDicts() {
        return ApiResult.ok(dataDictService.listDicts());
    }

    @PostMapping
    public ApiResult<DataDict> createDict(@RequestParam String code, @RequestParam String name,
                                           @RequestParam(required = false) String description) {
        return ApiResult.ok(dataDictService.createDict(code, name, description));
    }

    @PutMapping("/{id}")
    public ApiResult<DataDict> updateDict(@PathVariable Long id, @RequestParam String name,
                                           @RequestParam(required = false) String description) {
        return ApiResult.ok(dataDictService.updateDict(id, name, description));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteDict(@PathVariable Long id) {
        dataDictService.deleteDict(id);
        return ApiResult.ok();
    }

    // ===== 条目管理 =====

    @GetMapping("/{dictId}/items")
    public ApiResult<List<DataDictItem>> listItems(@PathVariable Long dictId) {
        return ApiResult.ok(dataDictService.listItems(dictId));
    }

    @PostMapping("/{dictId}/items")
    public ApiResult<DataDictItem> createItem(@PathVariable Long dictId, @RequestParam String code,
                                               @RequestParam String name, @RequestParam(defaultValue = "0") int sortOrder) {
        return ApiResult.ok(dataDictService.createItem(dictId, code, name, sortOrder));
    }

    @PutMapping("/items/{id}")
    public ApiResult<DataDictItem> updateItem(@PathVariable Long id, @RequestParam(required = false) String name,
                                               @RequestParam(required = false) Integer sortOrder,
                                               @RequestParam(required = false) Integer status) {
        return ApiResult.ok(dataDictService.updateItem(id, name, sortOrder, status));
    }

    @DeleteMapping("/items/{id}")
    public ApiResult<Void> deleteItem(@PathVariable Long id) {
        dataDictService.deleteItem(id);
        return ApiResult.ok();
    }
}
