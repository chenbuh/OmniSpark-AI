package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.entity.Notification;
import com.example.aihub.infrastructure.mapper.NotificationMapper;
import com.example.aihub.infrastructure.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 创建通知并实时推送
     */
    @Transactional(rollbackFor = Exception.class)
    public NotificationVO notify(Long userId, String title, String content, String type, Long relatedId) {
        Notification notif = new Notification();
        notif.setUserId(userId);
        notif.setTitle(title);
        notif.setContent(content);
        notif.setType(type != null ? type : "info");
        notif.setRelatedId(relatedId);
        notif.setIsRead(0);
        notif.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notif);

        NotificationVO vo = toVO(notif);
        // 通过 WebSocket 实时推送给用户
        try {
            messagingTemplate.convertAndSend("/topic/notifications/" + userId, vo);
        } catch (Exception ignored) {}
        return vo;
    }

    public List<NotificationVO> listUnread(Long userId) {
        return notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .orderByDesc(Notification::getId))
                .stream().map(this::toVO).toList();
    }

    public List<NotificationVO> listAll(Long userId, int limit) {
        return notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getId)
                        .last("LIMIT " + limit))
                .stream().map(this::toVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id) {
        Notification notif = notificationMapper.selectById(id);
        if (notif != null) {
            notif.setIsRead(1);
            notificationMapper.updateById(notif);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    public long countUnread(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    private NotificationVO toVO(Notification n) {
        return VoMapper.copy(n, NotificationVO.class);
    }
}
