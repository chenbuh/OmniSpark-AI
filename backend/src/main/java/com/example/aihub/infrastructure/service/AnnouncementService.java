package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.entity.Announcement;
import com.example.aihub.infrastructure.mapper.AnnouncementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementService {
    private final AnnouncementMapper announcementMapper;

    public PageResult<Announcement> page(Boolean activeOnly, long page, long pageSize) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(activeOnly)) {
            wrapper.eq(Announcement::getStatus, 1);
        }
        wrapper.orderByDesc(Announcement::getPriority)
                .orderByDesc(Announcement::getId);
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 20);
        Page<Announcement> result = announcementMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        return new PageResult<>(result.getTotal(), result.getPages(), result.getRecords());
    }

    public List<Announcement> list(Boolean activeOnly, int limit) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(activeOnly)) {
            wrapper.eq(Announcement::getStatus, 1);
        }
        wrapper.orderByDesc(Announcement::getPriority)
                .orderByDesc(Announcement::getId)
                .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100));
        return announcementMapper.selectList(wrapper);
    }

    public Announcement get(Long id) {
        Announcement ann = announcementMapper.selectById(id);
        if (ann == null) throw new BusinessException("公告不存在");
        return ann;
    }

    @Transactional(rollbackFor = Exception.class)
    public Announcement create(String title, String content, String priority, Long userId) {
        Announcement ann = new Announcement();
        ann.setTitle(title);
        ann.setContent(content);
        ann.setPriority(priority != null ? priority : "normal");
        ann.setStatus(1);
        ann.setCreatedBy(userId);
        announcementMapper.insert(ann);
        return ann;
    }

    @Transactional(rollbackFor = Exception.class)
    public Announcement update(Long id, String title, String content, String priority) {
        Announcement ann = announcementMapper.selectById(id);
        if (ann == null) throw new BusinessException("公告不存在");
        ann.setTitle(title);
        ann.setContent(content);
        ann.setPriority(priority != null ? priority : ann.getPriority());
        announcementMapper.updateById(ann);
        return ann;
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id) {
        Announcement ann = announcementMapper.selectById(id);
        if (ann == null) throw new BusinessException("公告不存在");
        ann.setStatus(ann.getStatus() == 1 ? 0 : 1);
        announcementMapper.updateById(ann);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Announcement ann = announcementMapper.selectById(id);
        if (ann == null) {
            throw new BusinessException("公告不存在");
        }
        announcementMapper.deleteById(id);
    }
}
