package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.util.SecurityUtil;
import com.example.aihub.infrastructure.entity.QuotaRecord;
import com.example.aihub.infrastructure.mapper.QuotaRecordMapper;
import com.example.aihub.infrastructure.vo.QuotaRecordVO;
import com.example.aihub.infrastructure.vo.QuotaSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuotaService {
    private static final int DEFAULT_QUOTA_LIMIT = 100;

    private final QuotaRecordMapper quotaRecordMapper;

    public QuotaSummaryVO summary() {
        List<QuotaRecord> records = quotaRecordMapper.selectList(new LambdaQueryWrapper<QuotaRecord>()
                .eq(QuotaRecord::getUserId, SecurityUtil.loginUserId()));
        int used = records.stream().mapToInt(item -> item.getAmount() == null ? 0 : item.getAmount()).sum();
        QuotaSummaryVO vo = new QuotaSummaryVO();
        vo.setQuotaLimit(DEFAULT_QUOTA_LIMIT);
        vo.setQuotaUsed(used);
        vo.setRemaining(Math.max(0, DEFAULT_QUOTA_LIMIT - used));
        return vo;
    }

    public List<QuotaRecordVO> records(Long projectId) {
        LambdaQueryWrapper<QuotaRecord> wrapper = new LambdaQueryWrapper<QuotaRecord>()
                .eq(QuotaRecord::getUserId, SecurityUtil.loginUserId());
        if (projectId != null) {
            wrapper.eq(QuotaRecord::getProjectId, projectId);
        }
        return quotaRecordMapper.selectList(wrapper).stream()
                .sorted(Comparator.comparing(QuotaRecord::getId).reversed())
                .map(item -> {
                    QuotaRecordVO vo = new QuotaRecordVO();
                    vo.setId(item.getId());
                    vo.setUserId(item.getUserId());
                    vo.setProjectId(item.getProjectId());
                    vo.setTaskId(item.getTaskId());
                    vo.setQuotaType(item.getQuotaType());
                    vo.setAmount(item.getAmount());
                    vo.setRemark(item.getRemark());
                    vo.setCreatedAt(item.getCreatedAt());
                    return vo;
                })
                .toList();
    }
}
