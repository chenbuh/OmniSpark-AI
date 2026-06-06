package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.entity.DataDict;
import com.example.aihub.infrastructure.entity.DataDictItem;
import com.example.aihub.infrastructure.mapper.DataDictItemMapper;
import com.example.aihub.infrastructure.mapper.DataDictMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DataDictService {
    private final DataDictMapper dictMapper;
    private final DataDictItemMapper itemMapper;

    // ===== 字典 CRUD =====

    public PageResult<DataDict> pageDicts(long page, long pageSize) {
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 20);
        Page<DataDict> result = dictMapper.selectPage(
                new Page<>(safePage, safePageSize),
                new LambdaQueryWrapper<DataDict>().orderByDesc(DataDict::getId)
        );
        return new PageResult<>(result.getTotal(), result.getPages(), result.getRecords());
    }

    public List<DataDict> listDicts(int limit) {
        return dictMapper.selectList(new LambdaQueryWrapper<DataDict>()
                .orderByDesc(DataDict::getId)
                .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)));
    }

    public DataDict getDict(Long id) {
        DataDict dict = dictMapper.selectById(id);
        if (dict == null) throw new BusinessException("字典不存在");
        return dict;
    }

    @Transactional(rollbackFor = Exception.class)
    public DataDict createDict(String code, String name, String description) {
        Long exists = dictMapper.selectCount(new LambdaQueryWrapper<DataDict>().eq(DataDict::getDictCode, code));
        if (exists != null && exists > 0) throw new BusinessException("字典编码已存在");
        DataDict dict = new DataDict();
        dict.setDictCode(code);
        dict.setDictName(name);
        dict.setDescription(description);
        dict.setStatus(1);
        dictMapper.insert(dict);
        return dict;
    }

    @Transactional(rollbackFor = Exception.class)
    public DataDict updateDict(Long id, String name, String description) {
        DataDict dict = dictMapper.selectById(id);
        if (dict == null) throw new BusinessException("字典不存在");
        dict.setDictName(name);
        dict.setDescription(description);
        dictMapper.updateById(dict);
        return dict;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDict(Long id) {
        DataDict dict = dictMapper.selectById(id);
        if (dict == null) {
            throw new BusinessException("字典不存在");
        }
        dictMapper.deleteById(id);
        itemMapper.delete(new LambdaQueryWrapper<DataDictItem>().eq(DataDictItem::getDictId, id));
    }

    // ===== 字典条目 CRUD =====

    public PageResult<DataDictItem> pageItems(Long dictId, long page, long pageSize) {
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 20);
        Page<DataDictItem> result = itemMapper.selectPage(
                new Page<>(safePage, safePageSize),
                new LambdaQueryWrapper<DataDictItem>()
                        .eq(DataDictItem::getDictId, dictId)
                        .orderByAsc(DataDictItem::getSortOrder)
                        .orderByAsc(DataDictItem::getId)
        );
        return new PageResult<>(result.getTotal(), result.getPages(), result.getRecords());
    }

    public List<DataDictItem> listItems(Long dictId, int limit) {
        return itemMapper.selectList(new LambdaQueryWrapper<DataDictItem>()
                .eq(DataDictItem::getDictId, dictId)
                .orderByAsc(DataDictItem::getSortOrder)
                .orderByAsc(DataDictItem::getId)
                .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)));
    }

    public DataDictItem getItem(Long id) {
        DataDictItem item = itemMapper.selectById(id);
        if (item == null) throw new BusinessException("条目不存在");
        return item;
    }

    public List<DataDictItem> getItemsByCode(String dictCode, int limit) {
        DataDict dict = dictMapper.selectOne(new LambdaQueryWrapper<DataDict>().eq(DataDict::getDictCode, dictCode));
        if (dict == null) return List.of();
        return listItems(dict.getId(), limit);
    }

    public List<DataDictItem> getEnabledItemsByCode(String dictCode, int limit) {
        DataDict dict = dictMapper.selectOne(new LambdaQueryWrapper<DataDict>()
                .eq(DataDict::getDictCode, dictCode)
                .eq(DataDict::getStatus, 1));
        if (dict == null) {
            return List.of();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<DataDictItem>()
                .eq(DataDictItem::getDictId, dict.getId())
                .eq(DataDictItem::getStatus, 1)
                .orderByAsc(DataDictItem::getSortOrder)
                .orderByAsc(DataDictItem::getId)
                .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)));
    }

    @Transactional(rollbackFor = Exception.class)
    public DataDictItem createItem(Long dictId, String code, String name, Integer sortOrder) {
        DataDict dict = dictMapper.selectById(dictId);
        if (dict == null) throw new BusinessException("字典不存在");
        DataDictItem item = new DataDictItem();
        item.setDictId(dictId);
        item.setItemCode(code);
        item.setItemName(name);
        item.setSortOrder(sortOrder != null ? sortOrder : 0);
        item.setStatus(1);
        itemMapper.insert(item);
        return item;
    }

    @Transactional(rollbackFor = Exception.class)
    public DataDictItem updateItem(Long id, String name, Integer sortOrder, Integer status) {
        DataDictItem item = itemMapper.selectById(id);
        if (item == null) throw new BusinessException("条目不存在");
        if (name != null) item.setItemName(name);
        if (sortOrder != null) item.setSortOrder(sortOrder);
        if (status != null) item.setStatus(status);
        itemMapper.updateById(item);
        return item;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        DataDictItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("条目不存在");
        }
        itemMapper.deleteById(id);
    }
}
